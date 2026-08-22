package com.filemngt.v2.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

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

/** FT-056: reduction chỉ nhận event durable mới, hội tụ theo source order và rebuild được từ raw stage. */
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
class CatalogOperationReductionIT {
    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    CatalogOperationStageStore stage;

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
    void insertedSliceMaintainsOneTypedWinnerPerSubjectAndLocator() {
        ensureOperation();
        var events = CatalogOperationBenchmarkFixture.sliceEvents(0, 10);

        int inserted = stage.ingest(events, CatalogOperationBenchmarkFixture.sliceCoordinates(0, 10));

        assertThat(inserted).isEqualTo(10);
        assertThat(count("catalog_operation_subject_reduction")).isEqualTo(1);
        assertThat(count("catalog_operation_asset_reduction")).isEqualTo(10);
        assertThat(reductionRecordCount()).isEqualTo(10);
    }

    @Test
    void lowerSourceCoordinateCannotReplaceExistingWinner() {
        ensureOperation();
        var original = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        var winner = copy(original, UUID.randomUUID(), "Artist_Alex - winner", original.timestamp().plusSeconds(1));
        var older = copy(original, UUID.randomUUID(), "Artist_Alex - older", original.timestamp());

        stage.ingest(
                List.of(winner, older),
                List.of(
                        new CatalogOperationStageStore.RecordCoordinate(4, 20),
                        new CatalogOperationStageStore.RecordCoordinate(4, 10)));

        String displayTitle = jdbc.queryForObject(
                "select display_title from catalog_operation_subject_reduction where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
        assertThat(displayTitle).isEqualTo("Artist_Alex - winner");
        String assetTitle = jdbc.queryForObject(
                "select display_title from catalog_operation_asset_reduction where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
        assertThat(assetTitle).isEqualTo("Artist_Alex - winner");
    }

    @Test
    void rebuildRestoresTypedReductionFromPreV22RawStage() {
        ensureOperation();
        stage.ingest(
                CatalogOperationBenchmarkFixture.sliceEvents(0, 10),
                CatalogOperationBenchmarkFixture.sliceCoordinates(0, 10));
        jdbc.update("delete from catalog_operation_asset_reduction");
        jdbc.update("delete from catalog_operation_subject_reduction");
        jdbc.update("""
                update catalog_approval_operation
                set reduction_version = 0, reduction_record_count = 0, reduction_completed_at = null
                where operation_id = ?
                """, CatalogOperationBenchmarkFixture.operationId());

        jdbc.queryForObject(
                "select catalog_rebuild_operation_reduction(?)",
                Object.class,
                CatalogOperationBenchmarkFixture.operationId());

        assertThat(count("catalog_operation_subject_reduction")).isEqualTo(1);
        assertThat(count("catalog_operation_asset_reduction")).isEqualTo(10);
        assertThat(reductionRecordCount()).isEqualTo(10);
    }

    private void ensureOperation() {
        jdbc.update("""
                insert into catalog_approval_operation(operation_id, scan_run_id)
                values (?, ?)
                on conflict (operation_id) do nothing
                """, CatalogOperationBenchmarkFixture.operationId(), CatalogOperationBenchmarkFixture.scanRunId());
    }

    private long count(String table) {
        Long count = jdbc.queryForObject(
                "select count(*) from " + table + " where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return count == null ? 0 : count;
    }

    private long reductionRecordCount() {
        Long count = jdbc.queryForObject(
                "select reduction_record_count from catalog_approval_operation where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return count == null ? 0 : count;
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
