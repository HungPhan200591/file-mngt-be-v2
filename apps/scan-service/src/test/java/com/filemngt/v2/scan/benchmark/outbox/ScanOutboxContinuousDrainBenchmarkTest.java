package com.filemngt.v2.scan.benchmark.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
import com.filemngt.v2.scan.application.outbox.OutboxInFlightWindow;
import com.filemngt.v2.scan.application.outbox.OutboxMessagePublisher;
import com.filemngt.v2.scan.application.outbox.ScanOutboxClaimService;
import com.filemngt.v2.scan.application.outbox.ScanOutboxDrainCoordinator;
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

/** Candidate FT-052 benchmark cùng fixture legacy, loại fixed delay nhưng vẫn không thay Kafka broker thật. */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(
        properties = {
            "scan.outbox.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false",
            "scan.approval-operation.enabled=false"
        })
class ScanOutboxContinuousDrainBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanOutboxContinuousDrainBenchmarkTest.class);
    private static final int WINDOW_SIZE = 500;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ScanOutboxEventRepository events;

    @Autowired
    ScanOutboxClaimService claims;

    @BeforeEach
    void resetDatabase() {
        OutboxDrainBenchmarkFixture.reset(jdbcTemplate);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void measuresContinuousDrainForTwentyFiveThousandEvents() {
        measureContinuousDrain(25_000);
    }

    @Test
    void measuresContinuousDrainForOneMillionEvents() {
        measureContinuousDrain(1_000_000);
    }

    private void measureContinuousDrain(int eventCount) {
        OutboxDrainBenchmarkFixture.seedPendingOutbox(jdbcTemplate, eventCount);
        ScanOutboxDrainCoordinator coordinator = coordinator();

        long started = System.nanoTime();
        while (events.countByPublishedAtIsNull() > 0) {
            coordinator.drainTimeSlice();
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertThat(events.countByPublishedAtIsNull()).isZero();
        LOGGER.info(
                "FT-052 continuous drain candidate: events={}, maxInFlight={}, claimSize={}, elapsedMs={}, throughputPerSecond={}",
                eventCount,
                WINDOW_SIZE,
                WINDOW_SIZE,
                elapsedMillis,
                throughput(eventCount, elapsedMillis));
    }

    private ScanOutboxDrainCoordinator coordinator() {
        OutboxDrainProperties properties = new OutboxDrainProperties();
        properties.setMaxInFlightEvents(WINDOW_SIZE);
        properties.setClaimSize(WINDOW_SIZE);
        properties.setDrainTimeSliceMs(100);
        properties.setIdleDelayMs(1);
        properties.setCompletionFlushSize(WINDOW_SIZE);
        OutboxInFlightWindow window = new OutboxInFlightWindow(properties);
        ScanOutboxMetrics metrics = new ScanOutboxMetrics(new SimpleMeterRegistry(), events, window);
        OutboxMessagePublisher immediateAcknowledgement = (topic, key, payload) -> {};
        return new ScanOutboxDrainCoordinator(
                claims,
                events,
                immediateAcknowledgement,
                window,
                properties,
                metrics,
                Tracer.NOOP,
                Propagator.NOOP,
                "outbox-continuous-drain");
    }

    private long throughput(int eventCount, long elapsedMillis) {
        return elapsedMillis == 0 ? 0 : Math.round(eventCount * 1_000.0 / elapsedMillis);
    }
}
