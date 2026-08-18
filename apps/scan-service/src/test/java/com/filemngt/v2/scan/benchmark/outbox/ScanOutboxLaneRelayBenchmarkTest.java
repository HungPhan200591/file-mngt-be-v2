package com.filemngt.v2.scan.benchmark.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.lane.ScanOutboxRelayLaneStore;
import com.filemngt.v2.scan.application.outbox.OutboxInFlightWindow;
import com.filemngt.v2.scan.application.outbox.OutboxMessagePublisher;
import com.filemngt.v2.scan.application.outbox.lane.ScanOutboxLaneRelayCoordinator;
import com.filemngt.v2.scan.benchmark.fixture.OutboxDrainBenchmarkFixture;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

/** FT-053 application/PostgreSQL ceiling; broker acknowledgement hoàn tất ngay để cô lập data plane. */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(
        properties = {
            "scan.outbox.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false",
            "scan.approval-operation.enabled=false",
            "scan.review-projection.enabled=false"
        })
class ScanOutboxLaneRelayBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanOutboxLaneRelayBenchmarkTest.class);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ScanOutboxEventRepository events;

    @Autowired
    ScanOutboxRelayLaneStore store;

    @BeforeEach
    void resetDatabase() {
        OutboxDrainBenchmarkFixture.reset(jdbc);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void measuresLaneRelayForTwentyFiveThousandEvents() {
        measureLaneRelay(25_000);
    }

    @Test
    void measuresLaneRelayForOneMillionEvents() {
        measureLaneRelay(1_000_000);
    }

    private void measureLaneRelay(int eventCount) {
        OutboxDrainBenchmarkFixture.seedPendingOutbox(jdbc, eventCount);
        var properties = properties();
        var coordinator = coordinator(properties);
        try {
            long started = System.nanoTime();
            int published = 0;
            int emptySlices = 0;
            while (published < eventCount) {
                int marked = coordinator.drainTimeSlice();
                published += marked;
                emptySlices = marked == 0 ? emptySlices + 1 : 0;
                assertThat(emptySlices).isLessThan(properties.getLaneCount() / properties.getLaneWorkerConcurrency());
            }
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

            assertThat(events.countByPublishedAtIsNull()).isZero();
            LOGGER.info(
                    "FT-053 lane relay: events={}, lanes={}, workers={}, fetchSize={}, elapsedMs={}, throughputPerSecond={}",
                    eventCount,
                    properties.getLaneCount(),
                    properties.getLaneWorkerConcurrency(),
                    properties.getLaneFetchSize(),
                    elapsedMillis,
                    throughput(eventCount, elapsedMillis));
        } finally {
            coordinator.close();
        }
    }

    private ScanOutboxLaneRelayCoordinator coordinator(OutboxDrainProperties properties) {
        OutboxInFlightWindow window = new OutboxInFlightWindow(properties);
        ScanOutboxMetrics metrics = new ScanOutboxMetrics(new SimpleMeterRegistry(), events, window);
        OutboxMessagePublisher immediateAcknowledgement = (topic, key, payload) -> {};
        return new ScanOutboxLaneRelayCoordinator(
                store, immediateAcknowledgement, properties, metrics, Tracer.NOOP, Propagator.NOOP, "lane-benchmark");
    }

    private OutboxDrainProperties properties() {
        var properties = new OutboxDrainProperties();
        properties.setLaneRelayEnabled(true);
        properties.setLaneCount(64);
        properties.setLaneWorkerConcurrency(4);
        properties.setLaneFetchSize(5_000);
        properties.setLaneMaxInFlightEvents(5_000);
        return properties;
    }

    private long throughput(int eventCount, long elapsedMillis) {
        return elapsedMillis == 0 ? 0 : Math.round(eventCount * 1_000.0 / elapsedMillis);
    }
}
