package com.filemngt.v2.catalog.application.operation;

import com.filemngt.v2.catalog.application.outbox.operation.CatalogOutboxPressureGate;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** FT-057: worker chỉ claim một coarse unit rồi gọi một reconciliation transaction set-based. */
@Component
@ConditionalOnProperty(name = "catalog.operation.finalizer-enabled", havingValue = "true")
public class CatalogOperationFinalizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationFinalizer.class);

    private final CatalogOperationUnitStore units;
    private final CatalogOperationFailureStore failures;
    private final CatalogOutboxPressureGate pressureGate;
    private final CatalogOperationFinalizerTelemetry telemetry;
    private final CatalogOperationReliabilityMetrics reliabilityMetrics;
    private final String owner;
    private final int workerCount;
    private final long leaseSeconds;
    private final int maximumAttempts;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    @Autowired
    public CatalogOperationFinalizer(
            CatalogOperationUnitStore units,
            CatalogOperationFailureStore failures,
            CatalogOutboxPressureGate pressureGate,
            CatalogOperationFinalizerTelemetry telemetry,
            CatalogOperationReliabilityMetrics reliabilityMetrics,
            CatalogOperationFinalizerSettings settings) {
        this.units = units;
        this.failures = failures;
        this.pressureGate = pressureGate;
        this.telemetry = telemetry;
        this.reliabilityMetrics = reliabilityMetrics;
        this.owner = settings.owner();
        this.workerCount = settings.workerCount();
        this.leaseSeconds = settings.leaseSeconds();
        this.maximumAttempts = settings.maximumAttempts();
    }

    @Scheduled(fixedDelayString = "${catalog.operation.finalizer-delay-ms:10}")
    public void finalizeReady() {
        if (!accepting.get() || pressureGate.isPaused()) return;
        List<CompletableFuture<Integer>> tasks = new ArrayList<>(workerCount);
        for (int worker = 0; worker < workerCount; worker++) {
            tasks.add(CompletableFuture.supplyAsync(this::processUnit, workers));
        }
        int processed = tasks.stream().mapToInt(CompletableFuture::join).sum();
        long completionStarted = System.nanoTime();
        int committing = units.beginCommittingEligibleOperations();
        long completionNanos = System.nanoTime() - completionStarted;
        telemetry.recordCompleteOperation(completionNanos);
        reliabilityMetrics.recordPhase("commit-gate", completionNanos);
        if (processed > 0 || committing > 0) {
            LOGGER.debug(
                    "Catalog operation finalizer processedSubjects={} committingOperations={}", processed, committing);
        }
    }

    @PreDestroy
    public void close() {
        accepting.set(false);
        workers.close();
    }

    private int processUnit() {
        long acquireStarted = System.nanoTime();
        Instant now = Instant.now();
        var claimed = units.acquire(owner, now, now.plusSeconds(leaseSeconds));
        telemetry.recordAcquire(System.nanoTime() - acquireStarted);
        if (claimed.isEmpty()) return 0;

        CatalogOperationUnitClaim unit = claimed.get();
        long reconcileStarted = System.nanoTime();
        try {
            int processed = units.reconcile(unit);
            long reconcileNanos = System.nanoTime() - reconcileStarted;
            telemetry.recordUnit(processed, reconcileNanos);
            reliabilityMetrics.recordPhase("reconcile", reconcileNanos);
            return processed;
        } catch (RuntimeException exception) {
            reliabilityMetrics.recordPhase("reconcile", System.nanoTime() - reconcileStarted);
            if (isSnapshotTooLarge(exception)) {
                failures.blockSnapshotTooLarge(unit);
                LOGGER.warn(
                        "Catalog operation blocked because a subject snapshot exceeds its limit operationId={} unit={}",
                        unit.operationId(),
                        unit.unitId());
            } else {
                handleRetryableFailure(unit, exception);
            }
            return 0;
        }
    }

    private void handleRetryableFailure(CatalogOperationUnitClaim unit, RuntimeException exception) {
        var disposition = failures.recordRetryOrBlock(
                unit, exception.getClass().getName(), exception.getMessage(), maximumAttempts);
        if (disposition == CatalogOperationFailureStore.FailureDisposition.RETRY_SCHEDULED) {
            reliabilityMetrics.recordRetry("reconcile-unit");
        }
        LOGGER.warn(
                "Catalog operation finalizer failure operationId={} unit={} disposition={} errorType={} causeType={}",
                unit.operationId(),
                unit.unitId(),
                disposition,
                exception.getClass().getSimpleName(),
                causeType(exception));
    }

    private static boolean isSnapshotTooLarge(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("SUBJECT_SNAPSHOT_TOO_LARGE"))
                return true;
        }
        return false;
    }

    private static String causeType(Throwable exception) {
        Throwable cause = exception.getCause();
        return cause == null ? "none" : cause.getClass().getSimpleName();
    }
}
