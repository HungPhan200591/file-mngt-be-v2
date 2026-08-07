package com.filemngt.v2.scan.application.stream;

import java.util.UUID;

/** Tín hiệu progress nội bộ; không phải event durable hay contract Kafka. */
public record ScanRunStreamProgress(UUID runId, ScanRunStreamPhase phase, ScanRunStreamCounters counters) {
    public static ScanRunStreamProgress durable(
            UUID runId,
            ScanRunStreamPhase phase,
            long scannedFileCount,
            Long changedFileCount,
            long reconciledFileCount,
            long proposalCount,
            long issueCount) {
        return new ScanRunStreamProgress(
                runId,
                phase,
                new ScanRunStreamCounters(
                        scannedFileCount,
                        scannedFileCount,
                        changedFileCount,
                        reconciledFileCount,
                        proposalCount,
                        issueCount));
    }
}
