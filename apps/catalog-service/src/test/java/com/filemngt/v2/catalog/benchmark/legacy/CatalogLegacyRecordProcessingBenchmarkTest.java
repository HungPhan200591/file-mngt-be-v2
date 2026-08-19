package com.filemngt.v2.catalog.benchmark.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.application.CatalogFileDiscoveryService;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
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

/** Baseline Catalog legacy: một transaction JPA và một snapshot outbox cho từng discovery event. */
@Tag("benchmark")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "p6spy.enabled=false",
            "logging.level.com.filemngt.v2.catalog.application.CatalogFileDiscoveryService=OFF"
        })
class CatalogLegacyRecordProcessingBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogLegacyRecordProcessingBenchmarkTest.class);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    CatalogFileDiscoveryService discoveryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        CatalogOperationBenchmarkFixture.reset(jdbcTemplate);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @Order(1)
    void measuresLegacyCatalogRecordProcessingForTwentyFiveThousandEvents() {
        measureLegacyPath(25_000);
    }

    @Test
    @Order(2)
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresLegacyCatalogRecordProcessingForOneMillionEvents() {
        measureLegacyPath(1_000_000);
    }

    private void measureLegacyPath(int eventCount) {
        warmUpAndReset();
        long processingNanos = 0;
        for (int index = 0; index < eventCount; index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Benchmark execution interrupted");
            }
            var event = CatalogOperationBenchmarkFixture.discoveryEvent(index);
            long started = System.nanoTime();
            discoveryService.handleV2(event);
            processingNanos += System.nanoTime() - started;
        }
        long elapsedMillis = elapsedMillis(processingNanos);

        assertThat(CatalogOperationBenchmarkFixture.processedEventCount(jdbcTemplate))
                .isEqualTo(eventCount);
        assertThat(CatalogOperationBenchmarkFixture.assetCount(jdbcTemplate)).isEqualTo(eventCount);
        assertThat(CatalogOperationBenchmarkFixture.outboxCount(jdbcTemplate)).isEqualTo(eventCount);
        long subjectCount = (eventCount + CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT - 1)
                / CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT;
        assertThat(CatalogOperationBenchmarkFixture.subjectCount(jdbcTemplate)).isEqualTo(subjectCount);

        LOGGER.info(
                "FT-054 legacy Catalog baseline: events={}, subjects={}, assetsPerSubject={}, handlerElapsedMs={}, "
                        + "recordsPerSecond={}, subjectsPerSecond={}",
                eventCount,
                subjectCount,
                CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT,
                elapsedMillis,
                throughput(eventCount, elapsedMillis),
                throughput((int) subjectCount, elapsedMillis));
    }

    private void warmUpAndReset() {
        for (int index = 0; index < CatalogOperationBenchmarkFixture.WARM_UP_EVENTS; index++) {
            discoveryService.handleV2(CatalogOperationBenchmarkFixture.discoveryEvent(index));
        }
        CatalogOperationBenchmarkFixture.reset(jdbcTemplate);
    }

    private long elapsedMillis(long processingNanos) {
        return Math.max(1L, processingNanos / 1_000_000L);
    }

    private long throughput(int eventCount, long elapsedMillis) {
        return Math.round(eventCount * 1_000.0 / elapsedMillis);
    }
}
