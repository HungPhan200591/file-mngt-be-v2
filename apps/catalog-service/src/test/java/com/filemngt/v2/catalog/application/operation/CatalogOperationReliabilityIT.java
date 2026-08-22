package com.filemngt.v2.catalog.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

/** FT-058 terminal reliability: lease reclaim, fenced retry exhaustion và total deadline. */
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
class CatalogOperationReliabilityIT {
    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CatalogOperationUnitStore units;

    @Autowired
    CatalogOperationFailureStore failures;

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
    void expiredLeaseIsReclaimedAndThirdFencedFailureBlocksOperation() {
        UUID operationId = insertOperation("RECONCILING", Instant.now().plusSeconds(120));
        insertRunningUnit(operationId, "crashed-worker", Instant.now().minusSeconds(1), 1);
        var staleClaim = new CatalogOperationUnitClaim(
                operationId, 0, "crashed-worker", Instant.now().minusSeconds(1), 1);

        var claim = units.acquire("reclaimer", Instant.now(), Instant.now().plusSeconds(30))
                .orElseThrow();
        assertThat(claim.fenceToken()).isEqualTo(2);
        assertThat(failures.recordRetryOrBlock(staleClaim, "QueryTimeout", "stale", 3))
                .isEqualTo(CatalogOperationFailureStore.FailureDisposition.STALE_FENCE);
        failures.blockSnapshotTooLarge(staleClaim);
        assertThat(operationStatus(operationId)).isEqualTo("RECONCILING");

        claim = failAndReacquire(claim, 1);
        claim = failAndReacquire(claim, 2);
        assertThat(failures.recordRetryOrBlock(claim, "QueryTimeout", "statement timeout", 3))
                .isEqualTo(CatalogOperationFailureStore.FailureDisposition.BLOCKED);

        assertThat(operationStatus(operationId)).isEqualTo("BLOCKED");
        assertThat(operationFailure(operationId)).isEqualTo("CATALOG_RETRY_EXHAUSTED");
        assertThat(unitAttemptCount(operationId)).isEqualTo(3);
    }

    @Test
    void watchdogBlocksEveryActivePhaseAfterTotalDeadline() {
        List<UUID> operations = List.of(
                insertOperation("INGESTING", Instant.now().minusSeconds(1)),
                insertOperation("RECONCILING", Instant.now().minusSeconds(1)),
                insertOperation("COMMITTING", Instant.now().minusSeconds(1)));
        CatalogOperationReliabilityMetrics metrics = mock(CatalogOperationReliabilityMetrics.class);
        var watchdog = new CatalogOperationDeadlineWatchdog(jdbc, metrics);

        assertThat(watchdog.blockExpiredOperations()).isEqualTo(3);
        assertThat(operations).allSatisfy(operationId -> {
            assertThat(operationStatus(operationId)).isEqualTo("BLOCKED");
            assertThat(operationFailure(operationId)).isEqualTo("CATALOG_OPERATION_DEADLINE_EXCEEDED");
        });
        verify(metrics).recordDeadlineBlocks(3);
    }

    private CatalogOperationUnitClaim failAndReacquire(CatalogOperationUnitClaim claim, int expectedAttempts) {
        assertThat(failures.recordRetryOrBlock(claim, "QueryTimeout", "statement timeout", 3))
                .isEqualTo(CatalogOperationFailureStore.FailureDisposition.RETRY_SCHEDULED);
        assertThat(unitAttemptCount(claim.operationId())).isEqualTo(expectedAttempts);
        return units.acquire("reclaimer", Instant.now(), Instant.now().plusSeconds(30))
                .orElseThrow();
    }

    private UUID insertOperation(String status, Instant deadline) {
        UUID operationId = UUID.randomUUID();
        Instant firstReceived = deadline.minusSeconds(120);
        jdbc.update(
                """
                insert into catalog_approval_operation(
                    operation_id, scan_run_id, processing_version, status, first_received_at, deadline_at)
                values (?, ?, 57, ?, ?, ?)
                """, operationId, UUID.randomUUID(), status, Timestamp.from(firstReceived), Timestamp.from(deadline));
        return operationId;
    }

    private void insertRunningUnit(UUID operationId, String owner, Instant leaseUntil, long fence) {
        jdbc.update("""
                insert into catalog_operation_reconcile_unit(
                    operation_id, unit_id, status, lease_owner, lease_until, fence_token)
                values (?, 0, 'RUNNING', ?, ?, ?)
                """, operationId, owner, Timestamp.from(leaseUntil), fence);
    }

    private String operationStatus(UUID operationId) {
        return jdbc.queryForObject(
                "select status from catalog_approval_operation where operation_id = ?", String.class, operationId);
    }

    private String operationFailure(UUID operationId) {
        return jdbc.queryForObject(
                "select failure_code from catalog_approval_operation where operation_id = ?",
                String.class,
                operationId);
    }

    private int unitAttemptCount(UUID operationId) {
        Integer attempts = jdbc.queryForObject(
                "select attempt_count from catalog_operation_reconcile_unit where operation_id = ? and unit_id = 0",
                Integer.class,
                operationId);
        return attempts == null ? 0 : attempts;
    }
}
