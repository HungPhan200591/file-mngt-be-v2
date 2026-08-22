package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** FT-055 / BT-09D1: Dedicated fast typed ingest benchmark đo độc lập throughput stage.ingest với 4 concurrent workers. */
@Tag("benchmark")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=false",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "p6spy.enabled=false"
        })
class CatalogOperationIngestBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationIngestBenchmarkTest.class);
    private static final int SLICE_SIZE = 5_000;
    private static final int WORKER_COUNT = 4;

    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES =
            (PostgreSQLContainer) new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"))
                    .withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw"))
                    .withCommand(
                            "postgres",
                            "-c",
                            "fsync=off",
                            "-c",
                            "synchronous_commit=off",
                            "-c",
                            "full_page_writes=off",
                            "-c",
                            "shared_buffers=512MB",
                            "-c",
                            "work_mem=32MB");

    @Autowired
    CatalogOperationStageStore stage;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CatalogOperationIngestTelemetry telemetry;

    @BeforeEach
    void resetDatabase() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
        if (telemetry != null) telemetry.reset();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @Order(1)
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void measuresIngestForTwentyFiveThousandEvents() {
        measureIngest(25_000);
    }

    @Test
    @Order(2)
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void measuresIngestForOneMillionEvents() {
        measureIngest(1_000_000);
    }

    private void measureIngest(int eventCount) {
        long started = System.nanoTime();
        try (var workers = Executors.newFixedThreadPool(WORKER_COUNT)) {
            var futures = new ArrayList<Future<?>>();
            for (int start = 0; start < eventCount; start += SLICE_SIZE) {
                int sliceStart = start;
                int count = Math.min(SLICE_SIZE, eventCount - start);
                futures.add(workers.submit(() -> {
                    var events = CatalogOperationBenchmarkFixture.sliceEvents(sliceStart, count);
                    var coordinates = CatalogOperationBenchmarkFixture.sliceCoordinates(sliceStart, count);
                    stage.ingest(events, coordinates);
                }));
            }
            for (var future : futures) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Benchmark interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Benchmark worker failed", exception.getCause());
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        long expectedSubjects = CatalogOperationBenchmarkFixture.expectedSubjects(eventCount);
        assertThat(
                        count(
                                "select coalesce(sum(inserted_record_count), 0) from catalog_operation_ingest_partition where operation_id = ?"))
                .isEqualTo(eventCount);
        assertThat(count("select count(*) from catalog_operation_discovery_input where operation_id = ?"))
                .isEqualTo(eventCount);

        var snap = telemetry != null ? telemetry.snapshot() : null;
        LOGGER.info(
                "FT-057 isolated immutable ingest: events={}, expectedSubjects={}, workers={}, wallClockMs={}, throughputPerSecond={}\n  -> {}",
                eventCount,
                expectedSubjects,
                WORKER_COUNT,
                elapsedMillis,
                throughput(eventCount, elapsedMillis),
                snap);
    }

    private long count(String sql) {
        var results = jdbc.queryForList(sql, Long.class, CatalogOperationBenchmarkFixture.operationId());
        return results.isEmpty() || results.getFirst() == null ? 0L : results.getFirst();
    }

    private long throughput(int eventCount, long elapsedMillis) {
        return elapsedMillis == 0 ? 0 : Math.round(eventCount * 1_000.0 / elapsedMillis);
    }
}
