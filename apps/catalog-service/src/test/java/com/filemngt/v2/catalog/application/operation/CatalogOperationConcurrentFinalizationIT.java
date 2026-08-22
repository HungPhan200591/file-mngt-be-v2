package com.filemngt.v2.catalog.application.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

/** Regression cho checkpoint đồng thời của coarse reconciliation units trên cùng một operation. */
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
class CatalogOperationConcurrentFinalizationIT {
    private static final int EVENT_COUNT = 4_000;
    private static final int CONCURRENT_UNITS = 4;

    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    CatalogOperationStageStore stage;

    @Autowired
    CatalogOperationSealStore seals;

    @Autowired
    CatalogOperationUnitStore units;

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
    void fourUnitsCheckpointConcurrentlyWithoutLockUpgradeDeadlock() throws Exception {
        List<MediaFileDiscoveredV2> events = CatalogOperationBenchmarkFixture.sliceEvents(0, EVENT_COUNT);
        stage.ingest(events, coordinates(events.size()));
        var first = events.getFirst();
        stage.acceptWatermark(CatalogOperationBenchmarkFixture.approvalCommittedWatermark(
                events.size(), first.operationId(), first.scanRunId()));
        assertThat(seals.sealNext(16)).isPresent();

        List<CatalogOperationUnitClaim> claims = acquireUnits();
        assertThat(claims).hasSize(CONCURRENT_UNITS);
        assertThat(claims).extracting(CatalogOperationUnitClaim::unitId).doesNotHaveDuplicates();
        assertThat(claims).allSatisfy(claim -> assertThat(workCount(claim)).isPositive());

        var ready = new CountDownLatch(CONCURRENT_UNITS);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> results = claims.stream()
                    .map(claim -> workers.submit(() -> reconcileTogether(claim, ready, start)))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isPositive();
            }
        }

        assertThat(completedUnitCount()).isEqualTo(CONCURRENT_UNITS);
        assertThat(operationAttemptCount()).isZero();
    }

    private List<CatalogOperationUnitClaim> acquireUnits() {
        var claims = new ArrayList<CatalogOperationUnitClaim>(CONCURRENT_UNITS);
        for (int index = 0; index < CONCURRENT_UNITS; index++) {
            claims.add(units.acquire(
                            "concurrent-it-" + index,
                            Instant.now(),
                            Instant.now().plusSeconds(30))
                    .orElseThrow());
        }
        return claims;
    }

    private int reconcileTogether(CatalogOperationUnitClaim claim, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent unit start timed out");
        return units.reconcile(claim);
    }

    private List<CatalogOperationStageStore.RecordCoordinate> coordinates(int eventCount) {
        var coordinates = new ArrayList<CatalogOperationStageStore.RecordCoordinate>(eventCount);
        for (int index = 0; index < eventCount; index++) {
            coordinates.add(new CatalogOperationStageStore.RecordCoordinate(index % 4, index));
        }
        return coordinates;
    }

    private int workCount(CatalogOperationUnitClaim claim) {
        Integer count = jdbc.queryForObject(
                "select count(*) from catalog_operation_work_subject where operation_id = ? and unit_id = ?",
                Integer.class,
                claim.operationId(),
                claim.unitId());
        return count == null ? 0 : count;
    }

    private int completedUnitCount() {
        Integer count = jdbc.queryForObject(
                "select count(*) from catalog_operation_reconcile_unit where status = 'COMPLETED'", Integer.class);
        return count == null ? 0 : count;
    }

    private int operationAttemptCount() {
        Integer attempts = jdbc.queryForObject("select attempt_count from catalog_approval_operation", Integer.class);
        return attempts == null ? -1 : attempts;
    }
}
