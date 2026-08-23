package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.adapter.out.persistence.outbox.operation.CatalogOutboxRelayLaneStore;
import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.application.CatalogOutboxMetrics;
import com.filemngt.v2.catalog.application.outbox.operation.CatalogOutboxRelayCoordinator;
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
    private static final int SUBJECT_COUNT = 100_000;
    private static final int INGEST_SLICE_SIZE = 5_000;

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
        var sampler = new CatalogPhysicalResourceSampler(jdbc);
        List<PhaseMeasurement> phases = new ArrayList<>();

        phases.add(sampler.measure("immutable-ingest", this::ingestSequentially));
        assertThat(count("catalog_operation_discovery_input")).isEqualTo(EVENT_COUNT);

        phases.add(sampler.measure(
                "aggregate-reduction",
                () -> inTransaction(() ->
                        CatalogPhysicalFeasibilitySql.reduce(jdbc, CatalogOperationBenchmarkFixture.operationId()))));
        assertThat(count("benchmark_catalog_subject_reduction")).isEqualTo(SUBJECT_COUNT);
        assertThat(count("benchmark_catalog_asset_reduction")).isEqualTo(EVENT_COUNT);

        phases.add(sampler.measure(
                "bulk-upsert-subject-assets",
                () -> inTransaction(() -> CatalogPhysicalFeasibilitySql.bulkUpsert(jdbc))));
        assertThat(count("media_subject")).isEqualTo(SUBJECT_COUNT);
        assertThat(count("media_asset")).isEqualTo(EVENT_COUNT);

        phases.add(sampler.measure(
                "create-outbox",
                () -> inTransaction(() -> CatalogPhysicalFeasibilitySql.createOutbox(
                        jdbc, CatalogOperationBenchmarkFixture.operationId()))));
        assertThat(pendingOutbox()).isEqualTo(SUBJECT_COUNT);

        phases.add(sampler.measure("relay-outbox-immediate-ack", this::relayOutboxSequentially));
        assertThat(pendingOutbox()).isZero();

        logResult(phases);
    }

    private void ingestSequentially() {
        for (int start = 0; start < EVENT_COUNT; start += INGEST_SLICE_SIZE) {
            int count = Math.min(INGEST_SLICE_SIZE, EVENT_COUNT - start);
            stage.ingest(
                    CatalogOperationBenchmarkFixture.sliceEvents(start, count),
                    CatalogOperationBenchmarkFixture.sliceCoordinates(start, count));
        }
    }

    private void relayOutboxSequentially() {
        CatalogOutboxRelayProperties properties = relayProperties();
        var coordinator = new CatalogOutboxRelayCoordinator(
                relayStore, (topic, key, payload) -> {}, properties, outboxMetrics, tracer, propagator);
        try {
            int published = 0;
            int maximumLanePasses = properties.getLaneCount() * 4;
            for (int pass = 0; pass < maximumLanePasses && published < SUBJECT_COUNT; pass++) {
                published += coordinator.drainTimeSlice();
            }
            assertThat(published).isEqualTo(SUBJECT_COUNT);
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

    private void logResult(List<PhaseMeasurement> phases) {
        long totalMillis =
                phases.stream().mapToLong(PhaseMeasurement::elapsedMillis).sum();
        LOGGER.info(
                "Catalog 1M sequential physical feasibility: processors={}, maxHeapMiB={}, totalMs={}, "
                        + "target120sMet={}\n{}",
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                totalMillis,
                totalMillis <= Duration.ofMinutes(2).toMillis(),
                phases.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
    }
}
