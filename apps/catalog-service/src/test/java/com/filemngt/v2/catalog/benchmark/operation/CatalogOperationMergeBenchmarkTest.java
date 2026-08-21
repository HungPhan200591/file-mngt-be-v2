package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.application.operation.CatalogOperationFinalizerTelemetry;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
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

/**
 * Đo {@code catalog_finalize_operation_page} (FT-056 CTE) trên finalizer Spring thật.
 * Seed ingest không nằm trong đồng hồ; {@code mergeMs} bắt đầu lúc {@code acceptWatermark}
 * tới workset {@code COMPLETED}. Không đợi {@code CATALOG_COMMITTED}, không enforce gate D2.
 */
@Tag("benchmark")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=true",
            "catalog.operation.worker-count=4",
            "catalog.operation.subject-page-size=500",
            "catalog.operation.finalizer-delay-ms=1",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=30",
            "p6spy.enabled=false"
        })
class CatalogOperationMergeBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationMergeBenchmarkTest.class);
    private static final int WORKER_COUNT = 4;
    private static final int PAGE_SIZE = 500;
    private static final int SEED_SLICE = 5_000;
    private static final int CALIBRATION_SUBJECTS = 2_500;
    private static final int QUALIFICATION_SUBJECTS = 100_000;
    private static final Duration WARM_UP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CALIBRATION_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration QUALIFICATION_TIMEOUT = Duration.ofMinutes(2);

    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES =
            (PostgreSQLContainer) new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"))
                    .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
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
    CatalogOperationFinalizerTelemetry telemetry;

    @BeforeEach
    void resetDatabase() {
        resetState();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @Order(1)
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresCteMergeForTwentyFiveHundredSubjects() {
        measureMerge(CALIBRATION_SUBJECTS, CALIBRATION_TIMEOUT);
    }

    @Test
    @Order(2)
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresCteMergeForOneHundredThousandSubjects() {
        measureMerge(QUALIFICATION_SUBJECTS, QUALIFICATION_TIMEOUT);
    }

    private void measureMerge(int subjectCount, Duration mergeTimeout) {
        int eventCount = CatalogOperationBenchmarkFixture.eventCountForSubjects(subjectCount);
        warmUpAndReset();
        long seedMs = seedEvents(eventCount);
        assertStaged(eventCount, subjectCount);
        telemetry.reset();
        long mergeStarted = System.nanoTime();
        stage.acceptWatermark(CatalogOperationBenchmarkFixture.approvalCommittedWatermark(eventCount));
        awaitWorksetCompleted(subjectCount, mergeTimeout);
        long mergeMs = elapsedMillis(mergeStarted);
        assertDurable(eventCount, subjectCount);
        logResult(eventCount, subjectCount, seedMs, mergeMs);
    }

    private void warmUpAndReset() {
        int warmUpEvents = CatalogOperationBenchmarkFixture.WARM_UP_EVENTS;
        seedEvents(warmUpEvents);
        stage.acceptWatermark(CatalogOperationBenchmarkFixture.approvalCommittedWatermark(warmUpEvents));
        awaitWorksetCompleted((int) CatalogOperationBenchmarkFixture.expectedSubjects(warmUpEvents), WARM_UP_TIMEOUT);
        resetState();
    }

    private long seedEvents(int eventCount) {
        long started = System.nanoTime();
        try (var workers = Executors.newFixedThreadPool(WORKER_COUNT)) {
            var tasks = new ArrayList<Future<?>>();
            for (int start = 0; start < eventCount; start += SEED_SLICE) {
                int sliceStart = start;
                int count = Math.min(SEED_SLICE, eventCount - start);
                tasks.add(workers.submit(() -> stage.ingest(
                        CatalogOperationBenchmarkFixture.sliceEvents(sliceStart, count),
                        CatalogOperationBenchmarkFixture.sliceCoordinates(sliceStart, count))));
            }
            for (var task : tasks) {
                task.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Merge benchmark seed interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Merge benchmark seed failed", exception.getCause());
        }
        return elapsedMillis(started);
    }

    private void awaitWorksetCompleted(int subjectCount, Duration timeout) {
        await().alias("finalizer drain workset to COMPLETED")
                .pollInterval(Duration.ofMillis(20))
                .atMost(timeout)
                .untilAsserted(() -> {
                    assertThat(count("""
                                    select count(*) from catalog_operation_subject
                                    where operation_id = ? and status = 'PENDING'
                                    """)).isZero();
                    assertThat(count("""
                                    select completed_subject_count from catalog_approval_operation
                                    where operation_id = ?
                                    """)).isEqualTo(subjectCount);
                });
    }

    private void assertStaged(int eventCount, int subjectCount) {
        assertThat(count("select received_record_count from catalog_approval_operation where operation_id = ?"))
                .isEqualTo(eventCount);
        assertThat(count("select count(*) from catalog_operation_subject where operation_id = ?"))
                .isEqualTo(subjectCount);
        assertThat(count("select count(*) from catalog_discovery_stage where operation_id = ?"))
                .isEqualTo(eventCount);
    }

    private void assertDurable(int eventCount, int subjectCount) {
        assertThat(CatalogOperationBenchmarkFixture.subjectCount(jdbc)).isEqualTo(subjectCount);
        assertThat(CatalogOperationBenchmarkFixture.assetCount(jdbc)).isEqualTo(eventCount);
        assertThat(count("""
                        select count(*) from catalog_outbox_event
                        where operation_id = ? and event_type = 'media.subject.changed.v2'
                        """)).isEqualTo(subjectCount);
    }

    private void logResult(int eventCount, int subjectCount, long seedMs, long mergeMs) {
        LOGGER.info(
                "FT-056 CTE merge: events={}, subjects={}, workers={}, pageSize={}, "
                        + "seedMs={}, mergeMs={}, subjectsPerSecond={}\n  -> {}",
                eventCount,
                subjectCount,
                WORKER_COUNT,
                PAGE_SIZE,
                seedMs,
                mergeMs,
                throughput(subjectCount, mergeMs),
                telemetry.snapshot());
    }

    private void resetState() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
        telemetry.reset();
    }

    private long count(String sql) {
        var results = jdbc.queryForList(sql, Long.class, CatalogOperationBenchmarkFixture.operationId());
        return results.isEmpty() || results.getFirst() == null ? 0L : results.getFirst();
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(1, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private static long throughput(int subjectCount, long elapsedMillis) {
        return Math.round(subjectCount * 1_000.0 / elapsedMillis);
    }
}
