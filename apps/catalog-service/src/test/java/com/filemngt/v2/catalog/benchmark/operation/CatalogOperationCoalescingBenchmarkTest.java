package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.contracts.events.MediaApprovalWatermarkV1;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("benchmark")
@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=true",
            "catalog.operation.worker-count=4",
            "catalog.operation.subject-page-size=2000",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "p6spy.enabled=false"
        })
class CatalogOperationCoalescingBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationCoalescingBenchmarkTest.class);
    private static final int SLICE_SIZE = 2_000;
    private static final int WARM_UP_EVENTS = 1_000;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    CatalogOperationStageStore stage;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
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
    void calibratesTwentyFiveThousandInputRecords() {
        measure(25_000, false);
    }

    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void qualifiesOneMillionInputRecordsWithinCanonicalBudget() {
        measure(1_000_000, true);
    }

    private void measure(int eventCount, boolean enforceBudget) {
        warmUpAndReset();
        long started = System.nanoTime();
        ingest(eventCount);
        stage.acceptWatermark(watermark(eventCount));
        awaitCatalogCommitted();
        long elapsedMillis =
                Math.max(1, Duration.ofNanos(System.nanoTime() - started).toMillis());
        long expectedSubjects = expectedSubjects(eventCount);

        assertThat(count("select received_record_count from catalog_approval_operation where operation_id = ?"))
                .isEqualTo(eventCount);
        assertThat(CatalogOperationBenchmarkFixture.subjectCount(jdbc)).isEqualTo(expectedSubjects);
        assertThat(CatalogOperationBenchmarkFixture.assetCount(jdbc)).isEqualTo(eventCount);
        assertThat(CatalogOperationBenchmarkFixture.outboxCount(jdbc)).isEqualTo(expectedSubjects + 1);
        if (enforceBudget) assertThat(elapsedMillis).isLessThanOrEqualTo(10_000);

        LOGGER.info(
                "FT-054 candidate: events={}, subjects={}, elapsedMs={}, recordsPerSecond={}",
                eventCount,
                expectedSubjects,
                elapsedMillis,
                Math.round(eventCount * 1_000.0 / elapsedMillis));
    }

    private void warmUpAndReset() {
        ingest(WARM_UP_EVENTS);
        stage.acceptWatermark(watermark(WARM_UP_EVENTS));
        awaitCatalogCommitted();
        CatalogOperationBenchmarkFixture.reset(jdbc);
    }

    private void ingest(int eventCount) {
        for (int start = 0; start < eventCount; start += SLICE_SIZE) {
            int end = Math.min(eventCount, start + SLICE_SIZE);
            var events = new ArrayList<com.filemngt.v2.contracts.events.MediaFileDiscoveredV2>(end - start);
            var coordinates = new ArrayList<CatalogOperationStageStore.RecordCoordinate>(end - start);
            for (int index = start; index < end; index++) {
                events.add(CatalogOperationBenchmarkFixture.discoveryEvent(index));
                coordinates.add(new CatalogOperationStageStore.RecordCoordinate(index % 12, index / 12L));
            }
            stage.ingest(events, coordinates);
        }
    }

    private MediaApprovalWatermarkV1 watermark(int eventCount) {
        return new MediaApprovalWatermarkV1(
                java.util.UUID.randomUUID(),
                "media.approval.watermark.v1",
                CatalogOperationBenchmarkFixture.operationId(),
                CatalogOperationBenchmarkFixture.scanRunId(),
                "APPROVAL_COMMITTED",
                10,
                eventCount,
                (long) eventCount,
                0L,
                (long) eventCount,
                null,
                null,
                null,
                0,
                (eventCount + 24_999L) / 25_000L,
                0,
                Instant.now(),
                null);
    }

    private void awaitCatalogCommitted() {
        Instant deadline = Instant.now().plusSeconds(240);
        while (!"CATALOG_COMMITTED".equals(status())) {
            if (Instant.now().isAfter(deadline)) throw new IllegalStateException("Catalog finalizer did not converge");
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    private String status() {
        return jdbc.queryForObject(
                "select status from catalog_approval_operation where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class, CatalogOperationBenchmarkFixture.operationId());
        return value == null ? 0 : value;
    }

    private long expectedSubjects(int eventCount) {
        return (eventCount + CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT - 1L)
                / CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT;
    }
}
