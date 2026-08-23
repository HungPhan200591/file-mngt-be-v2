package com.filemngt.v2.scan.benchmark.fixture;

import static com.filemngt.v2.scan.benchmark.fixture.ScanEndToEndBenchmarkSettings.POLL_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.approval.ApprovalOperationService;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.core.ThrowingRunnable;
import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

/** Await durable stage bằng một shared deadline và log DB snapshot khi thất bại. */
public final class ScanEndToEndBenchmarkAwaiter {
    private final JdbcTemplate jdbc;
    private final ScanRunRepository runs;
    private final ApprovalOperationService operations;
    private final Logger logger;

    public ScanEndToEndBenchmarkAwaiter(
            JdbcTemplate jdbc, ScanRunRepository runs, ApprovalOperationService operations, Logger logger) {
        this.jdbc = jdbc;
        this.runs = runs;
        this.operations = operations;
        this.logger = logger;
    }

    public void scanCompleted(UUID runId, long deadlineNanos) {
        awaitStage(new Stage("Scan core terminal completion", runId, null, deadlineNanos, () -> {
            var run = runs.findById(runId).orElseThrow();
            if (run.status() == ScanRunStatus.FAILED) throw new IllegalStateException(run.lastError());
            assertThat(run.status()).isEqualTo(ScanRunStatus.COMPLETED);
        }));
    }

    public void approvalCommitted(UUID runId, UUID operationId, long deadlineNanos) {
        awaitStage(new Stage("Scan approval operation completion", runId, operationId, deadlineNanos, () -> {
            var status = operations.status(operationId);
            if ("FAILED".equals(status.status()) || "BLOCKED".equals(status.status())) {
                throw new IllegalStateException("Approval operation failed: " + status.failureCode());
            }
            assertThat(status.status()).isEqualTo("APPROVAL_COMMITTED");
        }));
    }

    public void brokerAcknowledged(UUID runId, UUID operationId, long deadlineNanos) {
        awaitStage(new Stage(
                "Scan outbox final broker acknowledgement", runId, operationId, deadlineNanos, () -> assertThat(
                                pendingOutbox(operationId))
                        .isZero()));
    }

    private void awaitStage(Stage stage) {
        try {
            await().alias(stage.alias())
                    .pollInterval(POLL_INTERVAL)
                    .atMost(remaining(stage.deadlineNanos()))
                    .untilAsserted(stage.assertion());
        } catch (RuntimeException failure) {
            logger.error(
                    "Scan combined benchmark failed at {}: {}",
                    stage.alias(),
                    ScanEndToEndBenchmarkDiagnostics.describe(jdbc, stage.runId(), stage.operationId()));
            throw failure;
        }
    }

    private long pendingOutbox(UUID operationId) {
        Long count = jdbc.queryForObject(
                "select count(*) from scan_outbox_event where operation_id = ? and published_at is null",
                Long.class,
                operationId);
        return count == null ? 0 : count;
    }

    private Duration remaining(long deadlineNanos) {
        return Duration.ofNanos(Math.max(1, deadlineNanos - System.nanoTime()));
    }

    private record Stage(String alias, UUID runId, UUID operationId, long deadlineNanos, ThrowingRunnable assertion) {}
}
