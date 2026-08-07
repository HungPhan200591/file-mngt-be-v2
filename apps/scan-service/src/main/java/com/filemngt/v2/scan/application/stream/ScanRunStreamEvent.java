package com.filemngt.v2.scan.application.stream;

import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.time.Instant;
import java.util.UUID;

/** Payload versioned của dữ liệu SSE, không hứa replay hoặc exactly-once delivery. */
public record ScanRunStreamEvent(
        int schemaVersion,
        ScanRunStreamEventType eventType,
        UUID scanId,
        Instant emittedAt,
        ScanRunStreamPhase phase,
        ScanRunStatus status,
        long observedFileCount,
        long scannedFileCount,
        Long changedFileCount,
        Long reconciledFileCount,
        long proposalCount,
        long issueCount,
        Instant finishedAt,
        String lastError) {
    private static final int VERSION = 1;

    public static ScanRunStreamEvent progress(ScanRunStreamProgress progress) {
        var counters = progress.counters();
        return new ScanRunStreamEvent(
                VERSION,
                ScanRunStreamEventType.PROGRESS,
                progress.runId(),
                Instant.now(),
                progress.phase(),
                ScanRunStatus.RUNNING,
                counters.observedFileCount(),
                counters.scannedFileCount(),
                counters.changedFileCount(),
                counters.reconciledFileCount(),
                counters.proposalCount(),
                counters.issueCount(),
                null,
                null);
    }

    public String eventName() {
        return switch (eventType) {
            case SNAPSHOT -> "scan.snapshot.v1";
            case PROGRESS -> "scan.progress.v1";
            case TERMINAL -> "scan.terminal.v1";
        };
    }
}
