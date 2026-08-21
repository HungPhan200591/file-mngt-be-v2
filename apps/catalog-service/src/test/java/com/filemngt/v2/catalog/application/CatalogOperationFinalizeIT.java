package com.filemngt.v2.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemngt.v2.catalog.application.operation.CatalogOperationLaneStore;
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

/**
 * Parity FT-056 cho {@code catalog_finalize_operation_page}: subject mới/cũ, primary, tombstone,
 * actress, snapshot quá lớn và fence. Dùng PageStore/LaneStore thật, không bật scheduler.
 * Giữ một class vì các case dùng chung ingest/drain; vượt 250 dòng do số golden vector.
 */
@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=false",
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
    CatalogOperationLaneStore lanes;

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
        assertThat(CatalogOperationBenchmarkFixture.subjectCount(jdbc)).isEqualTo(1);
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
        Integer tagCount = jdbc.queryForObject("select count(*) from media_subject_tag", Integer.class);
        assertThat(tagCount).isZero();
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
        Integer actressCount = jdbc.queryForObject("select count(*) from actress", Integer.class);
        Integer registry = jdbc.queryForObject("select version from master_data_registry where id = 1", Integer.class);
        assertThat(actressCount).isEqualTo(1);
        assertThat(registry).isEqualTo(1);
    }

    @Test
    void oversizedSnapshotBlocksOperation() {
        var events = CatalogOperationBenchmarkFixture.sliceEvents(0, 10);
        ingest(events);
        openGate(events);
        drainWithMaximumSnapshotBytes(1);
        assertThat(operationStatus()).isEqualTo("BLOCKED");
        assertThat(worksetStatus()).isEqualTo("FAILED");
        assertThat(snapshotOutboxCount()).isZero();
    }

    @Test
    void wrongFenceIsRejected() {
        ingest(CatalogOperationBenchmarkFixture.sliceEvents(0, 10));
        openGate(CatalogOperationBenchmarkFixture.sliceEvents(0, 10));
        var claim = lanes.acquire(
                        "catalog-finalize-it", Instant.now(), Instant.now().plusSeconds(30))
                .orElseThrow();
        assertThatThrownBy(() -> jdbc.queryForObject(
                        "select catalog_finalize_operation_page(?, ?, ?, ?, ?, ?)",
                        Integer.class,
                        claim.operationId(),
                        claim.laneId(),
                        claim.owner(),
                        claim.fenceToken() + 1,
                        500,
                        921600))
                .hasMessageContaining("fence was lost");
    }

    private static List<MediaFileDiscoveredV2> tenEvents(UUID operationId, UUID scanRunId) {
        var events = new ArrayList<MediaFileDiscoveredV2>(10);
        for (int index = 0; index < 10; index++) {
            events.add(CatalogOperationBenchmarkFixture.discoveryEvent(index, operationId, scanRunId));
        }
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
    }

    private void drain() {
        drainWithMaximumSnapshotBytes(921600);
    }

    /** acquire theo lane_id; lane đầu thường trống nên phải lặp tới lane có workset. */
    private void drainWithMaximumSnapshotBytes(int maximumSnapshotBytes) {
        for (int step = 0; step < 128; step++) {
            var claim = lanes.acquire(
                    "catalog-finalize-it", Instant.now(), Instant.now().plusSeconds(30));
            if (claim.isEmpty()) {
                return;
            }
            var lane = claim.get();
            jdbc.queryForObject(
                    "select catalog_finalize_operation_page(?, ?, ?, ?, ?, ?)",
                    Integer.class,
                    lane.operationId(),
                    lane.laneId(),
                    lane.owner(),
                    lane.fenceToken(),
                    500,
                    maximumSnapshotBytes);
            if (!lanes.completeLaneIfDrained(lane, Instant.now())) {
                lanes.release(lane);
            }
            if ("BLOCKED".equals(operationStatus(lane.operationId()))) {
                return;
            }
        }
        throw new IllegalStateException("Catalog finalizer did not drain workset");
    }

    private void resetOperationTables() {
        jdbc.execute("""
                truncate table
                    catalog_operation_lane,
                    catalog_operation_subject,
                    catalog_discovery_stage,
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
        return jdbc.queryForObject("select status from catalog_operation_subject limit 1", String.class);
    }

    private String operationStatus() {
        return operationStatus(CatalogOperationBenchmarkFixture.operationId());
    }

    private String operationStatus(UUID operationId) {
        return jdbc.queryForObject(
                "select status from catalog_approval_operation where operation_id = ?",
                String.class,
                operationId);
    }
}
