package com.filemngt.v2.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemngt.v2.catalog.application.operation.CatalogOperationFailureStore;
import com.filemngt.v2.catalog.application.operation.CatalogOperationSealStore;
import com.filemngt.v2.catalog.application.operation.CatalogOperationUnitClaim;
import com.filemngt.v2.catalog.application.operation.CatalogOperationUnitStore;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** FT-057 parity: one set-based reconciliation transaction per coarse unit, no subject pages. */
@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=false",
            "catalog.operation.seal-enabled=false",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "p6spy.enabled=false"
        })
class CatalogOperationFinalizeIT {
    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    CatalogOperationStageStore stage;

    @Autowired
    CatalogOperationUnitStore units;

    @Autowired
    CatalogOperationFailureStore failures;

    @Autowired
    CatalogOperationSealStore seals;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
    }

    @Test
    void newSubjectMaterializesCanonicalAndOutbox() {
        ingestAndDrain(CatalogOperationBenchmarkFixture.sliceEvents(0, 10));
        assertThat(CatalogOperationBenchmarkFixture.subjectCount(jdbc)).isEqualTo(1);
        assertThat(CatalogOperationBenchmarkFixture.assetCount(jdbc)).isEqualTo(10);
        assertThat(snapshotOutboxCount()).isEqualTo(1);
        assertThat(subjectVersion()).isEqualTo(0);
        assertThat(worksetStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void existingUnchangedSkipsVersionAndOutbox() {
        ingestAndDrain(CatalogOperationBenchmarkFixture.sliceEvents(0, 10));
        long version = subjectVersion();
        resetOperationTables();
        UUID operationId = UUID.randomUUID();
        UUID scanRunId = UUID.randomUUID();
        ingestAndDrain(tenEvents(operationId, scanRunId));
        assertThat(subjectVersion()).isEqualTo(version);
        assertThat(snapshotOutboxCount(operationId)).isZero();
    }

    @Test
    void existingChangedBumpsVersionAndEmitsOutbox() {
        ingestAndDrain(CatalogOperationBenchmarkFixture.sliceEvents(0, 10));
        resetOperationTables();
        UUID operationId = UUID.randomUUID();
        UUID scanRunId = UUID.randomUUID();
        var events = new ArrayList<MediaFileDiscoveredV2>();
        for (int index = 0; index < 10; index++) {
            events.add(CatalogOperationBenchmarkFixture.discoveryEvent(
                    index, operationId, scanRunId, "Artist_Alex - changed", List.of("HD")));
        }
        ingestAndDrain(events);
        assertThat(subjectVersion()).isEqualTo(1);
        assertThat(snapshotOutboxCount(operationId)).isEqualTo(1);
    }

    @Test
    void untaggedVideoWinsPrimaryElectionAndSubjectTagsFollowPrimary() {
        ingestAndDrain(CatalogOperationBenchmarkFixture.sliceEvents(0, 10));
        String primaryPath =
                jdbc.queryForObject("select relative_path from media_asset where role = 'PRIMARY_VIDEO'", String.class);
        assertThat(primaryPath).endsWith("asset-01.mp4");
        assertThat(jdbc.queryForObject("select count(*) from media_subject_tag", Integer.class))
                .isZero();
    }

    @Test
    void tombstoneSuppressesOlderDiscovery() {
        var event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        jdbc.update(
                """
                insert into catalog_removed_asset_locator(storage_key, relative_path, removed_at)
                values (?, ?, ?)
                """,
                event.storageKey(),
                event.relativePath(),
                Timestamp.from(event.timestamp().plus(1, ChronoUnit.HOURS)));
        ingestAndDrain(List.of(event));
        assertThat(CatalogOperationBenchmarkFixture.assetCount(jdbc)).isZero();
    }

    @Test
    void actressInsertBumpsRegistryOnce() {
        ingestAndDrain(CatalogOperationBenchmarkFixture.sliceEvents(0, 10));
        assertThat(jdbc.queryForObject("select count(*) from actress", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select version from master_data_registry where id = 1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void emitsFinalWatermarkOnlyAfterEverySubjectSnapshotIsAcknowledged() {
        ingestAndDrain(CatalogOperationBenchmarkFixture.sliceEvents(0, 10));

        assertThat(units.beginCommittingEligibleOperations()).isZero();
        assertThat(operationStatus()).isEqualTo("RECONCILING");

        jdbc.update("""
                update catalog_outbox_event set published_at = now()
                where operation_id = ? and event_type = 'media.subject.changed.v2'
                """, CatalogOperationBenchmarkFixture.operationId());
        assertThat(units.beginCommittingEligibleOperations()).isEqualTo(1);
        assertThat(operationStatus()).isEqualTo("COMMITTING");

        jdbc.update("""
                update catalog_outbox_event set published_at = now()
                where operation_id = ? and event_type = 'media.approval.watermark.v1'
                """, CatalogOperationBenchmarkFixture.operationId());
        assertThat(operationStatus()).isEqualTo("CATALOG_COMMITTED");
    }

    @Test
    void oversizedSnapshotBlocksOperationInSeparateFailureTransaction() {
        var events = CatalogOperationBenchmarkFixture.sliceEvents(0, 10);
        ingest(events);
        openGate(events);
        var claim = claimForWork();
        assertThatThrownBy(() -> jdbc.queryForObject(
                        "select catalog_reconcile_operation_unit(?, ?, ?, ?, ?)",
                        Integer.class,
                        claim.operationId(),
                        claim.unitId(),
                        claim.owner(),
                        claim.fenceToken(),
                        1))
                .hasMessageContaining("SUBJECT_SNAPSHOT_TOO_LARGE");
        failures.blockSnapshotTooLarge(claim);

        assertThat(operationStatus()).isEqualTo("BLOCKED");
        assertThat(worksetStatus()).isEqualTo("PENDING");
        assertThat(snapshotOutboxCount()).isZero();
    }

    @Test
    void wrongFenceIsRejected() {
        var events = CatalogOperationBenchmarkFixture.sliceEvents(0, 10);
        ingest(events);
        openGate(events);
        var claim = units.acquire(
                        "catalog-finalize-it", Instant.now(), Instant.now().plusSeconds(30))
                .orElseThrow();
        assertThatThrownBy(() -> jdbc.queryForObject(
                        "select catalog_reconcile_operation_unit(?, ?, ?, ?, ?)",
                        Integer.class,
                        claim.operationId(),
                        claim.unitId(),
                        claim.owner(),
                        claim.fenceToken() + 1,
                        921600))
                .hasMessageContaining("fence was lost");
    }

    private static List<MediaFileDiscoveredV2> tenEvents(UUID operationId, UUID scanRunId) {
        var events = new ArrayList<MediaFileDiscoveredV2>(10);
        for (int index = 0; index < 10; index++)
            events.add(CatalogOperationBenchmarkFixture.discoveryEvent(index, operationId, scanRunId));
        return events;
    }

    private void ingestAndDrain(List<MediaFileDiscoveredV2> events) {
        ingest(events);
        openGate(events);
        drain();
    }

    private void ingest(List<MediaFileDiscoveredV2> events) {
        var coordinates = new ArrayList<CatalogOperationStageStore.RecordCoordinate>(events.size());
        for (int index = 0; index < events.size(); index++) {
            coordinates.add(new CatalogOperationStageStore.RecordCoordinate(index % 12, index));
        }
        stage.ingest(events, coordinates);
    }

    private void openGate(List<MediaFileDiscoveredV2> events) {
        var first = events.getFirst();
        stage.acceptWatermark(CatalogOperationBenchmarkFixture.approvalCommittedWatermark(
                events.size(), first.operationId(), first.scanRunId()));
        assertThat(seals.sealNext(16)).isPresent();
    }

    private void drain() {
        for (int step = 0; step < 64; step++) {
            var claim = units.acquire(
                    "catalog-finalize-it", Instant.now(), Instant.now().plusSeconds(30));
            if (claim.isEmpty()) return;
            units.reconcile(claim.orElseThrow());
        }
        throw new IllegalStateException("Catalog finalizer did not drain reconciliation units");
    }

    private CatalogOperationUnitClaim claimForWork() {
        for (int step = 0; step < 64; step++) {
            var claim = units.acquire(
                            "catalog-finalize-it", Instant.now(), Instant.now().plusSeconds(30))
                    .orElseThrow();
            Integer work = jdbc.queryForObject(
                    "select count(*) from catalog_operation_work_subject where operation_id = ? and unit_id = ?",
                    Integer.class,
                    claim.operationId(),
                    claim.unitId());
            if (work != null && work > 0) return claim;
            units.reconcile(claim);
        }
        throw new IllegalStateException("No reconciliation unit contains the test subject");
    }

    private void resetOperationTables() {
        jdbc.execute("""
                truncate table
                    catalog_operation_reconcile_unit,
                    catalog_operation_work_subject,
                    catalog_operation_ingest_partition,
                    catalog_operation_discovery_input,
                    catalog_approval_operation,
                    catalog_outbox_event,
                    catalog_processed_event,
                    catalog_dead_letter_event
                cascade
                """);
    }

    private long snapshotOutboxCount() {
        return snapshotOutboxCount(CatalogOperationBenchmarkFixture.operationId());
    }

    private long snapshotOutboxCount(UUID operationId) {
        Long count = jdbc.queryForObject("""
                select count(*) from catalog_outbox_event
                where operation_id = ? and event_type = 'media.subject.changed.v2'
                """, Long.class, operationId);
        return count == null ? 0 : count;
    }

    private long subjectVersion() {
        Long version = jdbc.queryForObject("select version from media_subject", Long.class);
        return version == null ? -1 : version;
    }

    private String worksetStatus() {
        return jdbc.queryForObject("select status from catalog_operation_work_subject limit 1", String.class);
    }

    private String operationStatus() {
        return jdbc.queryForObject(
                "select status from catalog_approval_operation where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
    }
}
