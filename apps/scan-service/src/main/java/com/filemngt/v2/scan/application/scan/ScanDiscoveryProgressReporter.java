package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.application.stream.ScanRunStreamCounters;
import com.filemngt.v2.scan.application.stream.ScanRunStreamPhase;
import com.filemngt.v2.scan.application.stream.ScanRunStreamProgress;
import java.util.UUID;

/** Báo discovery tối đa một lần mỗi interval mà không thêm DB write vào đường COPY. */
final class ScanDiscoveryProgressReporter {
    private static final long REPORT_EVERY_FILES = 1024L;

    private final ScanExecutionLiveness liveness;
    private final long intervalNanos;
    private final UUID runId;
    private final long durableFiles;
    private long observedFiles;
    private long nextSignalNanos = System.nanoTime();

    ScanDiscoveryProgressReporter(
            ScanExecutionLiveness liveness,
            long progressIntervalMillis,
            UUID runId,
            long durableFiles) {
        this.liveness = liveness;
        intervalNanos = progressIntervalMillis * 1_000_000L;
        this.runId = runId;
        this.durableFiles = durableFiles;
    }

    void recordFile() {
        observedFiles++;
        if (observedFiles % REPORT_EVERY_FILES != 0 || System.nanoTime() < nextSignalNanos) {
            return;
        }
        var counters = new ScanRunStreamCounters(
                durableFiles + observedFiles, durableFiles, null, 0L, 0L, 0L);
        liveness.publishTransient(new ScanRunStreamProgress(runId, ScanRunStreamPhase.DISCOVERY, counters));
        nextSignalNanos = System.nanoTime() + intervalNanos;
    }
}
