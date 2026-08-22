package com.filemngt.v2.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.filemngt.v2.catalog.application.operation.CatalogOperationReliabilityMetrics;
import com.filemngt.v2.catalog.application.operation.CatalogOperationSealCoordinator;
import com.filemngt.v2.catalog.application.operation.CatalogOperationSealStore;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** FT-058 seal gate với committed progress từ bốn ingest transaction đồng thời. */
@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=false",
            "catalog.operation.seal-enabled=false",
            "catalog.operation.watchdog-enabled=false",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "p6spy.enabled=false"
        })
class CatalogOperationSealIT {
    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    CatalogOperationStageStore stage;

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
    void watermarkFirstFourConcurrentPartitionsSealExactlyTwentyFiveThousandInputs() throws Exception {
        int eventCount = 25_000;
        stage.acceptWatermark(CatalogOperationBenchmarkFixture.approvalCommittedWatermark(eventCount));
        assertThat(seals.sealNext(16)).isEmpty();

        assertThat(ingestConcurrently(eventCount)).isEqualTo(eventCount);
        assertThat(operationStatus()).isEqualTo("INGESTING");
        assertThat(stageRowCount()).isEqualTo(eventCount);
        assertThat(seals.sealNext(16))
                .hasValueSatisfying(result -> assertThat(result.sealed()).isTrue());
        assertThat(operationStatus()).isEqualTo("RECONCILING");
        assertThat(worksetCount()).isEqualTo(eventCount / CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT);
    }

    @Test
    void inputFirstDoesNotSealUntilCommittedWatermarkArrives() {
        int eventCount = 40;
        stage.ingest(
                CatalogOperationBenchmarkFixture.sliceEvents(0, eventCount),
                CatalogOperationBenchmarkFixture.sliceCoordinates(0, eventCount));
        assertThat(seals.sealNext(16)).isEmpty();

        stage.acceptWatermark(CatalogOperationBenchmarkFixture.approvalCommittedWatermark(eventCount));

        assertThat(seals.sealNext(16))
                .hasValueSatisfying(result -> assertThat(result.sealed()).isTrue());
        assertThat(stageRowCount()).isEqualTo(eventCount);
    }

    @Test
    void reconstructedCoordinatorSealsPersistedCandidateWithoutInMemoryState() {
        int eventCount = 40;
        stage.ingest(
                CatalogOperationBenchmarkFixture.sliceEvents(0, eventCount),
                CatalogOperationBenchmarkFixture.sliceCoordinates(0, eventCount));
        stage.acceptWatermark(CatalogOperationBenchmarkFixture.approvalCommittedWatermark(eventCount));

        var restarted =
                new CatalogOperationSealCoordinator(seals, mock(CatalogOperationReliabilityMetrics.class), 16, 8);
        restarted.sealReady();

        assertThat(operationStatus()).isEqualTo("RECONCILING");
        assertThat(worksetCount()).isEqualTo(eventCount / CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT);
    }

    private int ingestConcurrently(int eventCount) throws Exception {
        List<Future<Integer>> futures = new ArrayList<>(4);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int partition = 0; partition < 4; partition++) {
                int worker = partition;
                futures.add(workers.submit(() -> ingestPartition(worker, eventCount / 4)));
            }
            int inserted = 0;
            for (Future<Integer> future : futures) inserted += future.get(60, TimeUnit.SECONDS);
            return inserted;
        }
    }

    private int ingestPartition(int partition, int count) {
        int start = partition * count;
        var events = CatalogOperationBenchmarkFixture.sliceEvents(start, count);
        var coordinates = new ArrayList<CatalogOperationStageStore.RecordCoordinate>(count);
        for (int offset = 0; offset < count; offset++) {
            coordinates.add(new CatalogOperationStageStore.RecordCoordinate(partition, offset));
        }
        return stage.ingest(events, coordinates);
    }

    private long stageRowCount() {
        return count("catalog_operation_discovery_input");
    }

    private long worksetCount() {
        return count("catalog_operation_work_subject");
    }

    private long count(String table) {
        Long count = jdbc.queryForObject(
                "select count(*) from " + table + " where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return count == null ? 0 : count;
    }

    private String operationStatus() {
        return jdbc.queryForObject(
                "select status from catalog_approval_operation where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
    }
}
