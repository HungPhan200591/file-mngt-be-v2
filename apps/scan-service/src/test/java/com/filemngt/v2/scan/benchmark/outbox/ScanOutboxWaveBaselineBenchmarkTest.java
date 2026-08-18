package com.filemngt.v2.scan.benchmark.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
import com.filemngt.v2.scan.application.outbox.OutboxMessagePublisher;
import com.filemngt.v2.scan.application.outbox.ScanOutboxClaimService;
import com.filemngt.v2.scan.application.outbox.ScanOutboxPublisher;
import com.filemngt.v2.scan.benchmark.fixture.OutboxDrainBenchmarkFixture;
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

/** Baseline FT-052 cho legacy wave relay với acknowledgement tức thì và fixed delay giữa các wave. */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(
        properties = {
            "scan.outbox.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false",
            "scan.approval-operation.enabled=false"
        })
class ScanOutboxWaveBaselineBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanOutboxWaveBaselineBenchmarkTest.class);
    private static final int BATCH_SIZE = 500;
    private static final long FIXED_DELAY_MILLIS = 50;

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
    void measuresLegacyWaveDrainForTwentyFiveThousandEvents() throws InterruptedException {
        measureLegacyWaveDrain(25_000);
    }

    @Test
    void measuresLegacyWaveDrainForOneMillionEvents() throws InterruptedException {
        measureLegacyWaveDrain(1_000_000);
    }

    private void measureLegacyWaveDrain(int eventCount) throws InterruptedException {
        OutboxDrainBenchmarkFixture.seedPendingOutbox(jdbcTemplate, eventCount);
        ScanOutboxPublisher publisher = publisher();
        int waveCount = (eventCount + BATCH_SIZE - 1) / BATCH_SIZE;

        long started = System.nanoTime();
        for (int wave = 0; wave < waveCount; wave++) {
            publisher.publishPending();
            if (wave + 1 < waveCount) Thread.sleep(FIXED_DELAY_MILLIS);
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertThat(events.countByPublishedAtIsNull()).isZero();
        LOGGER.info(
                "FT-052 legacy wave baseline: events={}, batchSize={}, fixedDelayMs={}, waves={}, "
                        + "elapsedMs={}, throughputPerSecond={}",
                eventCount,
                BATCH_SIZE,
                FIXED_DELAY_MILLIS,
                waveCount,
                elapsedMillis,
                throughput(eventCount, elapsedMillis));
    }

    private ScanOutboxPublisher publisher() {
        OutboxMessagePublisher immediateAcknowledgement = (topic, key, payload) -> {};
        ScanOutboxMetrics metrics = new ScanOutboxMetrics(new SimpleMeterRegistry(), events);
        return new ScanOutboxPublisher(
                events,
                claims,
                immediateAcknowledgement,
                metrics,
                Tracer.NOOP,
                Propagator.NOOP,
                "outbox-wave-baseline",
                BATCH_SIZE);
    }

    private long throughput(int eventCount, long elapsedMillis) {
        return elapsedMillis == 0 ? 0 : Math.round(eventCount * 1_000.0 / elapsedMillis);
    }
}
