package com.filemngt.v2.scan.application.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Ghi timing persistence sau completion để không nhầm write đã rollback là durable. */
final class ScanChunkCommitTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanChunkCommitTelemetry.class);

    Measurement begin(ScanExecutionTimeline timeline, ScanChunkCommitter.ChunkLease lease, int chunkIndex) {
        var measurement = new Measurement(timeline, chunkIndex);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            LOGGER.warn(
                    "Không có transaction synchronization cho timing chunk: runId={}, chunk={}",
                    lease.runId(),
                    chunkIndex);
            return measurement;
        }
        TransactionSynchronizationManager.registerSynchronization(measurement);
        return measurement;
    }

    final class Measurement implements TransactionSynchronization {
        private final ScanExecutionTimeline timeline;
        private final int chunkIndex;
        private final long startedNanos = System.nanoTime();
        private long inventoryWriteMillis;
        private long proposalCopyMillis;
        private long issueCopyMillis;
        private long checkpointMillis;
        private long workCompletedNanos = -1;

        private Measurement(ScanExecutionTimeline timeline, int chunkIndex) {
            this.timeline = timeline;
            this.chunkIndex = chunkIndex;
        }

        void inventoryWritten(long durationMillis) {
            inventoryWriteMillis = durationMillis;
        }

        void proposalsCopied(long durationMillis) {
            proposalCopyMillis = durationMillis;
        }

        void issuesCopied(long durationMillis) {
            issueCopyMillis = durationMillis;
        }

        void checkpointWritten(long durationMillis) {
            checkpointMillis = durationMillis;
            workCompletedNanos = System.nanoTime();
        }

        @Override
        public void afterCompletion(int status) {
            long completedNanos = System.nanoTime();
            var outcome = outcome(status);
            var timing = new ScanExecutionTimeline.ChunkCommitTiming(
                    chunkIndex,
                    outcome,
                    inventoryWriteMillis,
                    proposalCopyMillis,
                    issueCopyMillis,
                    checkpointMillis,
                    elapsedMillis(workCompletedNanos, completedNanos),
                    elapsedMillis(startedNanos, completedNanos));
            timeline.recordChunkCommit(timing);
            LOGGER.atInfo()
                    .addKeyValue("event", "scan.reconciliation.chunk.persistence")
                    .addKeyValue("chunk", chunkIndex)
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("inventoryWriteMs", inventoryWriteMillis)
                    .addKeyValue("proposalCopyMs", proposalCopyMillis)
                    .addKeyValue("issueCopyMs", issueCopyMillis)
                    .addKeyValue("checkpointMs", checkpointMillis)
                    .addKeyValue("commitMs", timing.commitMillis())
                    .addKeyValue("totalMs", timing.totalMillis())
                    .log(
                            "scan.reconciliation.chunk.persistence: chunk={}, outcome={}, inventoryWriteMs={}, proposalCopyMs={}, "
                                    + "issueCopyMs={}, checkpointMs={}, commitMs={}, totalMs={}",
                            chunkIndex,
                            outcome,
                            inventoryWriteMillis,
                            proposalCopyMillis,
                            issueCopyMillis,
                            checkpointMillis,
                            timing.commitMillis(),
                            timing.totalMillis());
        }

        private ScanExecutionTimeline.ChunkCommitOutcome outcome(int status) {
            return switch (status) {
                case STATUS_COMMITTED -> ScanExecutionTimeline.ChunkCommitOutcome.COMMITTED;
                case STATUS_ROLLED_BACK -> ScanExecutionTimeline.ChunkCommitOutcome.ROLLED_BACK;
                default -> ScanExecutionTimeline.ChunkCommitOutcome.UNKNOWN;
            };
        }

        private long elapsedMillis(long started, long completed) {
            return started < 0 ? 0 : (completed - started) / 1_000_000L;
        }
    }
}
