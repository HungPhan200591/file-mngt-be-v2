package com.filemngt.v2.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.application.operation.CatalogOperationSealStore;
import com.filemngt.v2.catalog.application.operation.CatalogOperationUnitStore;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.time.Instant;
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

/** FT-057: typed input là immutable; seal tạo workset đúng một lần, winner chỉ tính lúc reconcile. */
@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=false",
            "catalog.operation.seal-enabled=false",
            "catalog.operation.default-processing-version=57",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "p6spy.enabled=false"
        })
class CatalogOperationReductionIT {
    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    CatalogOperationStageStore stage;

    @Autowired
    CatalogOperationUnitStore units;

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
    void sealBuildsOneImmutableWorksetAndConfiguredCoarseUnits() {
        var events = CatalogOperationBenchmarkFixture.sliceEvents(0, 10);
        stage.ingest(events, CatalogOperationBenchmarkFixture.sliceCoordinates(0, 10));
        openGate(events);

        assertThat(count("catalog_operation_discovery_input")).isEqualTo(10);
        assertThat(count("catalog_operation_work_subject")).isEqualTo(1);
        assertThat(count("catalog_operation_reconcile_unit")).isEqualTo(16);
        assertThat(sum("subject_count", "catalog_operation_reconcile_unit")).isEqualTo(1);
    }

    @Test
    void sourceOrderElectsWinnerDuringReconcileRatherThanDuringIngest() {
        var original = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        var winner = copy(
                original,
                UUID.randomUUID(),
                "Artist_Alex - winner",
                original.timestamp().plusSeconds(1));
        var older = copy(original, UUID.randomUUID(), "Artist_Alex - older", original.timestamp());
        stage.ingest(
                List.of(winner, older),
                List.of(
                        new CatalogOperationStageStore.RecordCoordinate(4, 20),
                        new CatalogOperationStageStore.RecordCoordinate(4, 10)));
        openGate(List.of(winner, older));

        drainUnits();

        String displayTitle = jdbc.queryForObject("select display_title from media_subject", String.class);
        assertThat(displayTitle).isEqualTo("Artist_Alex - winner");
    }

    @Test
    void duplicateManifestDoesNotRebuildSealedWorkset() {
        var events = CatalogOperationBenchmarkFixture.sliceEvents(0, 10);
        stage.ingest(events, CatalogOperationBenchmarkFixture.sliceCoordinates(0, 10));
        MediaFileDiscoveredV2 first = events.getFirst();
        var manifest = CatalogOperationBenchmarkFixture.approvalCommittedWatermark(
                events.size(), first.operationId(), first.scanRunId());
        stage.acceptWatermark(manifest);
        stage.acceptWatermark(manifest);
        assertThat(seals.sealNext(16)).isPresent();

        assertThat(count("catalog_operation_work_subject")).isEqualTo(1);
        assertThat(count("catalog_operation_reconcile_unit")).isEqualTo(16);
    }

    private void openGate(List<MediaFileDiscoveredV2> events) {
        MediaFileDiscoveredV2 first = events.getFirst();
        stage.acceptWatermark(CatalogOperationBenchmarkFixture.approvalCommittedWatermark(
                events.size(), first.operationId(), first.scanRunId()));
        assertThat(seals.sealNext(16)).isPresent();
    }

    private void drainUnits() {
        for (int step = 0; step < 64; step++) {
            var claim =
                    units.acquire("reduction-it", Instant.now(), Instant.now().plusSeconds(30));
            if (claim.isEmpty()) return;
            units.reconcile(claim.orElseThrow());
        }
        throw new IllegalStateException("Reconciliation units did not drain");
    }

    private long count(String table) {
        Long value = jdbc.queryForObject(
                "select count(*) from " + table + " where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return value == null ? 0 : value;
    }

    private long sum(String column, String table) {
        Long value = jdbc.queryForObject(
                "select coalesce(sum(" + column + "), 0) from " + table + " where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return value == null ? 0 : value;
    }

    private static MediaFileDiscoveredV2 copy(
            MediaFileDiscoveredV2 event, UUID eventId, String displayTitle, Instant timestamp) {
        return new MediaFileDiscoveredV2(
                eventId,
                event.eventType(),
                timestamp,
                event.operationId(),
                event.batchId(),
                event.scanRunId(),
                event.proposalId(),
                event.region(),
                event.subjectType(),
                event.identityKey(),
                event.baseCode(),
                event.part(),
                event.studioCode(),
                displayTitle,
                event.actressNames(),
                event.tagNames(),
                event.role(),
                event.storageKey(),
                event.relativePath());
    }
}
