package com.filemngt.v2.catalog.application.operation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thu thập và thống kê timing từng phase của Catalog Finalizer:
 * coarse-unit lease acquire, set-based reconciliation, relay/completion.
 */
@Component
public class CatalogOperationFinalizerTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationFinalizerTelemetry.class);

    private final AtomicLong totalAcquireNanos = new AtomicLong();
    private final AtomicLong totalDrainNanos = new AtomicLong();
    private final AtomicLong completeOperationNanos = new AtomicLong();
    private final AtomicLong hybridReadNanos = new AtomicLong();
    private final AtomicLong hybridReduceNanos = new AtomicLong();
    private final AtomicLong hybridCopyNanos = new AtomicLong();
    private final AtomicLong hybridApplyNanos = new AtomicLong();
    private final AtomicInteger pageCount = new AtomicInteger();
    private final AtomicLong totalSubjects = new AtomicLong();
    private final ConcurrentLinkedQueue<Long> pageLatenciesNanos = new ConcurrentLinkedQueue<>();

    public void recordAcquire(long acquireNanos) {
        totalAcquireNanos.addAndGet(acquireNanos);
    }

    public void recordPage(int subjects, long executeNanos) {
        recordUnit(subjects, executeNanos);
    }

    public void recordUnit(int subjects, long executeNanos) {
        pageCount.incrementAndGet();
        totalSubjects.addAndGet(subjects);
        pageLatenciesNanos.add(executeNanos);

        LOGGER.atDebug()
                .addKeyValue("event", "catalog.finalizer.unit")
                .addKeyValue("subjects", subjects)
                .addKeyValue("executeMs", toMillis(executeNanos))
                .log("Catalog finalizer unit timing recorded");
    }

    public void recordDrain(long drainNanos) {
        totalDrainNanos.addAndGet(drainNanos);
    }

    public void recordCompleteOperation(long completeNanos) {
        completeOperationNanos.set(completeNanos);
    }

    public void recordHybridPhases(long readNanos, long reduceNanos, long copyNanos, long applyNanos) {
        hybridReadNanos.addAndGet(readNanos);
        hybridReduceNanos.addAndGet(reduceNanos);
        hybridCopyNanos.addAndGet(copyNanos);
        hybridApplyNanos.addAndGet(applyNanos);
    }

    public void reset() {
        totalAcquireNanos.set(0);
        totalDrainNanos.set(0);
        completeOperationNanos.set(0);
        hybridReadNanos.set(0);
        hybridReduceNanos.set(0);
        hybridCopyNanos.set(0);
        hybridApplyNanos.set(0);
        pageCount.set(0);
        totalSubjects.set(0);
        pageLatenciesNanos.clear();
    }

    public Snapshot snapshot() {
        List<Long> latencies = new ArrayList<>(pageLatenciesNanos);
        Collections.sort(latencies);

        long minMs = 0;
        long maxMs = 0;
        long avgMs = 0;
        long p95Ms = 0;
        long totalExecuteNanos = 0;

        if (!latencies.isEmpty()) {
            minMs = toMillis(latencies.getFirst());
            maxMs = toMillis(latencies.getLast());
            for (long lat : latencies) totalExecuteNanos += lat;
            avgMs = toMillis(totalExecuteNanos / latencies.size());
            int p95Index = (int) Math.ceil(latencies.size() * 0.95) - 1;
            p95Ms = toMillis(latencies.get(Math.max(0, p95Index)));
        }

        return new Snapshot(
                pageCount.get(),
                totalSubjects.get(),
                toMillis(totalAcquireNanos.get()),
                toMillis(totalExecuteNanos),
                minMs,
                avgMs,
                p95Ms,
                maxMs,
                toMillis(totalDrainNanos.get()),
                toMillis(completeOperationNanos.get()),
                toMillis(hybridReadNanos.get()),
                toMillis(hybridReduceNanos.get()),
                toMillis(hybridCopyNanos.get()),
                toMillis(hybridApplyNanos.get()));
    }

    private static long toMillis(long nanos) {
        return Math.max(0, Duration.ofNanos(nanos).toMillis());
    }

    public record Snapshot(
            int pageCount,
            long totalSubjects,
            long acquireMillis,
            long totalPageExecuteMillis,
            long minPageMillis,
            long avgPageMillis,
            long p95PageMillis,
            long maxPageMillis,
            long drainMillis,
            long completeOperationMillis,
            long hybridReadMillis,
            long hybridReduceMillis,
            long hybridCopyMillis,
            long hybridApplyMillis) {
        @Override
        public String toString() {
            return String.format(
                    "FinalizerSnapshot[units=%d, subjects=%d, acquire=%dms, unitExecTotal=%dms "
                            + "(min=%dms, avg=%dms, p95=%dms, max=%dms), drain=%dms, completeOp=%dms, "
                            + "hybrid=(read=%dms, reduce=%dms, copy=%dms, apply=%dms)]",
                    pageCount,
                    totalSubjects,
                    acquireMillis,
                    totalPageExecuteMillis,
                    minPageMillis,
                    avgPageMillis,
                    p95PageMillis,
                    maxPageMillis,
                    drainMillis,
                    completeOperationMillis,
                    hybridReadMillis,
                    hybridReduceMillis,
                    hybridCopyMillis,
                    hybridApplyMillis);
        }
    }
}
