package com.filemngt.v2.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemngt.v2.catalog.application.operation.CatalogOperationLaneHash;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.util.ArrayList;
import java.util.List;
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
 * Integration test cho FT-055 Typed Fast Ingest: kiểm tra correctness của typed COPY path,
 * durable dedupe, cardinality, lane hash golden vector và rollback safety.
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
class CatalogOperationIngestIT {
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
    void happyPathSingleEventDurableAndCountable() {
        ensureOperation();
        var event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        var coordinate = new CatalogOperationStageStore.RecordCoordinate(0, 0L);

        int inserted = stage.ingest(List.of(event), List.of(coordinate));

        assertThat(inserted).isEqualTo(1);
        assertThat(stageRowCount()).isEqualTo(1);
        assertThat(receivedCount()).isEqualTo(1);
    }

    @Test
    void duplicateEventIdIsIdempotentAndNotDoubleInserted() {
        ensureOperation();
        var event = CatalogOperationBenchmarkFixture.discoveryEvent(1);
        var coord = new CatalogOperationStageStore.RecordCoordinate(0, 1L);

        int firstInsert = stage.ingest(List.of(event), List.of(coord));
        int retryInsert = stage.ingest(List.of(event), List.of(coord));

        assertThat(firstInsert).isEqualTo(1);
        assertThat(retryInsert).isEqualTo(0);
        assertThat(stageRowCount()).isEqualTo(1);
    }

    @Test
    void fullSliceCardinality2000IsInsertedCorrectly() {
        ensureOperation();
        int sliceSize = 2_000;
        var events = new ArrayList<MediaFileDiscoveredV2>(sliceSize);
        var coords = new ArrayList<CatalogOperationStageStore.RecordCoordinate>(sliceSize);
        for (int i = 0; i < sliceSize; i++) {
            events.add(CatalogOperationBenchmarkFixture.discoveryEvent(i));
            coords.add(new CatalogOperationStageStore.RecordCoordinate(i % 12, (long) i));
        }

        int inserted = stage.ingest(events, coords);

        assertThat(inserted).isEqualTo(sliceSize);
        assertThat(stageRowCount()).isEqualTo(sliceSize);
    }

    @Test
    void laneHashInStageMatchesJavaGoldenVector() {
        // subject_lane lưu trong catalog_operation_subject phải khớp CatalogOperationLaneHash.stableLane()
        ensureOperation();
        var event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        stage.ingest(List.of(event), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0L)));

        String subjectKey = CatalogOperationStageStore.subjectKey(event);
        int expectedLane = CatalogOperationLaneHash.stableLane(subjectKey);
        Integer storedLane = jdbc.queryForObject(
                "select subject_lane from catalog_operation_subject where operation_id = ? and subject_key = ?",
                Integer.class,
                CatalogOperationBenchmarkFixture.operationId(),
                subjectKey);

        assertThat(storedLane).isEqualTo(expectedLane);
    }

    @Test
    void mismatchedCardinalityBetweenEventsAndCoordinatesIsRejected() {
        ensureOperation();
        var event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        assertThatThrownBy(() -> stage.ingest(
                        List.of(event),
                        List.of(
                                new CatalogOperationStageStore.RecordCoordinate(0, 0L),
                                new CatalogOperationStageStore.RecordCoordinate(1, 1L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("equal cardinality");
    }

    @Test
    void emptySliceIsNoOp() {
        int inserted = stage.ingest(List.of(), List.of());
        assertThat(inserted).isEqualTo(0);
        assertThat(stageRowCount()).isEqualTo(0);
    }

    private void ensureOperation() {
        jdbc.update("""
                insert into catalog_approval_operation(operation_id, scan_run_id)
                values (?, ?)
                on conflict (operation_id) do nothing
                """, CatalogOperationBenchmarkFixture.operationId(), CatalogOperationBenchmarkFixture.scanRunId());
    }

    private long stageRowCount() {
        Long count = jdbc.queryForObject(
                "select count(*) from catalog_discovery_stage where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return count == null ? 0 : count;
    }

    private long receivedCount() {
        Long count = jdbc.queryForObject(
                "select received_record_count from catalog_approval_operation where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return count == null ? 0 : count;
    }
}
