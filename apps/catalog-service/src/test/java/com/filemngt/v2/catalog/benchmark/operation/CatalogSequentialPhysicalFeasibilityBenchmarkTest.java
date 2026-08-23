package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.adapter.out.persistence.outbox.operation.CatalogOutboxRelayLaneStore;
import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.application.CatalogOutboxMetrics;
import com.filemngt.v2.catalog.application.outbox.operation.CatalogOutboxRelayCoordinator;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogBoundedPhaseExecutor;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogPhysicalFeasibilitySql;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogPhysicalResourceSampler;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogPhysicalResourceSampler.PhaseMeasurement;
import com.filemngt.v2.catalog.config.CatalogOutboxRelayProperties;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Đo physical lower-bound 1M theo phase tuần tự; không scheduler, Kafka hay overlap. */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.outbox.operation-relay.enabled=false",
            "catalog.operation.finalizer-enabled=false",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=8",
            "p6spy.enabled=false"
        })
class CatalogSequentialPhysicalFeasibilityBenchmarkTest {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(CatalogSequentialPhysicalFeasibilityBenchmarkTest.class);
    private static final int EVENT_COUNT = 1_000_000;
    private static final int CALIBRATION_EVENT_COUNT = 25_000;
    private static final int CALIBRATION_REPETITIONS = 3;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"))
            .withCommand("postgres", "-c", "track_io_timing=on", "-c", "track_wal_io_timing=on");

    @Autowired
    CatalogOperationStageStore stage;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    CatalogOutboxRelayLaneStore relayStore;

    @Autowired
    CatalogOutboxMetrics outboxMetrics;

    @Autowired
    Tracer tracer;

    @Autowired
    Propagator propagator;

    @BeforeEach
    void resetDatabase() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
        CatalogPhysicalFeasibilitySql.initializeScratch(jdbc);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresOneMillionRecordPhysicalLowerBoundSequentially() {
        measure(EVENT_COUNT, 1, 1);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void validatesTwentyFiveThousandRecordsThreeTimesWithTwoBoundedUpsertWorkers() {
        repeatCalibration(1, 2);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresOneMillionRecordsWithTwoBoundedUpsertWorkers() {
        measure(EVENT_COUNT, 1, 2);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void validatesTwentyFiveThousandRecordsThreeTimesWithFourBoundedUpsertWorkers() {
        repeatCalibration(1, 4);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresOneMillionRecordsWithFourBoundedUpsertWorkers() {
        measure(EVENT_COUNT, 1, 4);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void validatesTwentyFiveThousandRecordsThreeTimesWithFourSharedFenceIngestWorkers() {
        repeatCalibration(4, 2);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresOneMillionRecordsWithFourSharedFenceIngestWorkers() {
        measure(EVENT_COUNT, 4, 2);
    }

    private void repeatCalibration(int ingestWorkerCount, int upsertWorkerCount) {
        for (int repetition = 0; repetition < CALIBRATION_REPETITIONS; repetition++) {
            if (repetition > 0) resetDatabase();
            measure(CALIBRATION_EVENT_COUNT, ingestWorkerCount, upsertWorkerCount);
        }
    }

    private void measure(int eventCount, int ingestWorkerCount, int upsertWorkerCount) {
        int subjectCount = Math.toIntExact(CatalogOperationBenchmarkFixture.expectedSubjects(eventCount));
        CatalogPhysicalFeasibilitySql.initializeOperation(
                jdbc, CatalogOperationBenchmarkFixture.operationId(), CatalogOperationBenchmarkFixture.scanRunId());
        var sampler = new CatalogPhysicalResourceSampler(jdbc);
        var executor = new CatalogBoundedPhaseExecutor(stage, jdbc, transactions);
        List<PhaseMeasurement> phases = new ArrayList<>();

        phases.add(sampler.measure("immutable-ingest", () -> {
            if (ingestWorkerCount == 1) executor.ingestSequentially(eventCount);
            else executor.ingestBySourcePartition(eventCount, ingestWorkerCount);
        }));
        assertThat(count("catalog_operation_discovery_input")).isEqualTo(eventCount);

        phases.add(sampler.measure(
                "aggregate-reduction",
                () -> inTransaction(() ->
                        CatalogPhysicalFeasibilitySql.reduce(jdbc, CatalogOperationBenchmarkFixture.operationId()))));
        assertThat(count("benchmark_catalog_subject_reduction")).isEqualTo(subjectCount);
        assertThat(count("benchmark_catalog_asset_reduction")).isEqualTo(eventCount);

        phases.add(sampler.measure("bulk-upsert-subject-assets", () -> executor.bulkUpsert(upsertWorkerCount)));
        assertThat(count("media_subject")).isEqualTo(subjectCount);
        assertThat(count("media_asset")).isEqualTo(eventCount);

        phases.add(sampler.measure(
                "create-outbox",
                () -> inTransaction(() -> CatalogPhysicalFeasibilitySql.createOutbox(
                        jdbc, CatalogOperationBenchmarkFixture.operationId()))));
        assertThat(pendingOutbox()).isEqualTo(subjectCount);

        phases.add(sampler.measure("relay-outbox-immediate-ack", () -> relayOutboxSequentially(subjectCount)));
        assertThat(pendingOutbox()).isZero();
        assertTelemetryClean(phases);

        logResult(eventCount, subjectCount, ingestWorkerCount, upsertWorkerCount, phases);
    }

    private void relayOutboxSequentially(int expectedOutboxCount) {
        CatalogOutboxRelayProperties properties = relayProperties();
        var coordinator = new CatalogOutboxRelayCoordinator(
                relayStore, (topic, key, payload) -> {}, properties, outboxMetrics, tracer, propagator);
        try {
            int published = 0;
            int maximumLanePasses = properties.getLaneCount() * 4;
            for (int pass = 0; pass < maximumLanePasses && published < expectedOutboxCount; pass++) {
                published += coordinator.drainTimeSlice();
            }
            assertThat(published).isEqualTo(expectedOutboxCount);
        } finally {
            coordinator.close();
        }
    }

    private CatalogOutboxRelayProperties relayProperties() {
        var properties = new CatalogOutboxRelayProperties();
        properties.setWorkerCount(1);
        properties.setFetchSize(2_000);
        properties.setMaxInFlight(500);
        properties.setLeaseSeconds(300);
        properties.setIdleBackoffMillis(1);
        properties.setMaximumFailureBackoffMillis(1);
        properties.setInstanceId("catalog-physical-feasibility");
        return properties;
    }

    private void inTransaction(Runnable work) {
        transactions.executeWithoutResult(status -> work.run());
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }

    private long pendingOutbox() {
        Long count =
                jdbc.queryForObject("select count(*) from catalog_outbox_event where published_at is null", Long.class);
        return count == null ? 0 : count;
    }

    private void assertTelemetryClean(List<PhaseMeasurement> phases) {
        assertThat(phases).allSatisfy(phase -> {
            assertThat(phase.deadlocks()).as(phase.phase() + " deadlocks").isZero();
            assertThat(phase.maximumLockWaiters())
                    .as(phase.phase() + " lock waiters")
                    .isZero();
            assertThat(phase.samplingFailures())
                    .as(phase.phase() + " sampling failures")
                    .isZero();
        });
    }

    private void logResult(
            int eventCount,
            int subjectCount,
            int ingestWorkerCount,
            int upsertWorkerCount,
            List<PhaseMeasurement> phases) {
        long totalMillis =
                phases.stream().mapToLong(PhaseMeasurement::elapsedMillis).sum();
        LOGGER.info(
                "Catalog bounded physical feasibility: events={}, subjects={}, ingestWorkers={}, "
                        + "upsertWorkers={}, processors={}, maxHeapMiB={}, totalMs={}, target90sMet={}, "
                        + "target120sMet={}\n{}",
                eventCount,
                subjectCount,
                ingestWorkerCount,
                upsertWorkerCount,
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                totalMillis,
                totalMillis <= Duration.ofSeconds(90).toMillis(),
                totalMillis <= Duration.ofMinutes(2).toMillis(),
                phases.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
    }
}
