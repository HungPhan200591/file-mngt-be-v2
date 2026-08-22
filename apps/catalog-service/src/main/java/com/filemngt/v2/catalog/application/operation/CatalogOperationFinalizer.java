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
import org.springframework.beans.factory.annotation.Value;
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
    private final String owner;
    private final int workerCount;
    private final long leaseSeconds;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    @Autowired
    public CatalogOperationFinalizer(
            CatalogOperationUnitStore units,
            CatalogOperationFailureStore failures,
            CatalogOutboxPressureGate pressureGate,
            CatalogOperationFinalizerTelemetry telemetry,
            @Value("${catalog.operation.instance-id:${HOSTNAME:catalog-finalizer}}") String owner,
            @Value("${catalog.operation.worker-count:4}") int workerCount,
            @Value("${catalog.operation.lease-seconds:30}") long leaseSeconds) {
        this.units = units;
        this.failures = failures;
        this.pressureGate = pressureGate;
        this.telemetry = telemetry;
        this.owner = owner;
        this.workerCount = positive(workerCount, "worker-count");
        this.leaseSeconds = positive(leaseSeconds, "lease-seconds");
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
        telemetry.recordCompleteOperation(System.nanoTime() - completionStarted);
        if (processed > 0 || committing > 0) {
            LOGGER.debug("Catalog operation finalizer processedSubjects={} committingOperations={}", processed, committing);
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
        try {
            long reconcileStarted = System.nanoTime();
            int processed = units.reconcile(unit);
            telemetry.recordUnit(processed, System.nanoTime() - reconcileStarted);
            return processed;
        } catch (RuntimeException exception) {
            if (isSnapshotTooLarge(exception)) {
                failures.blockSnapshotTooLarge(unit);
                LOGGER.warn("Catalog operation blocked because a subject snapshot exceeds its limit operationId={} unit={}",
                        unit.operationId(), unit.unitId());
            } else {
                releaseAfterFailure(unit);
                LOGGER.warn(
                        "Catalog operation finalizer retryable failure operationId={} unit={} errorType={} causeType={}",
                        unit.operationId(), unit.unitId(), exception.getClass().getSimpleName(), causeType(exception));
            }
            return 0;
        }
    }

    private void releaseAfterFailure(CatalogOperationUnitClaim unit) {
        try {
            units.release(unit);
        } catch (RuntimeException releaseFailure) {
            LOGGER.warn("Catalog operation finalizer could not release failed unit operationId={} unit={} errorType={}",
                    unit.operationId(), unit.unitId(), releaseFailure.getClass().getSimpleName());
        }
    }

    private static boolean isSnapshotTooLarge(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("SUBJECT_SNAPSHOT_TOO_LARGE")) return true;
        }
        return false;
    }

    private static String causeType(Throwable exception) {
        Throwable cause = exception.getCause();
        return cause == null ? "none" : cause.getClass().getSimpleName();
    }

    private static int positive(int value, String property) {
        if (value < 1) throw new IllegalArgumentException("catalog.operation." + property + " must be positive");
        return value;
    }

    private static long positive(long value, String property) {
        if (value < 1) throw new IllegalArgumentException("catalog.operation." + property + " must be positive");
        return value;
    }
}
