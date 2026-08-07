package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.application.scan.deadline.ScanLeaseDeadlineGuard;
import com.filemngt.v2.scan.application.stream.ScanRunStreamPhase;
import com.filemngt.v2.scan.application.stream.ScanRunStreamProgress;
import com.filemngt.v2.scan.application.stream.ScanRunStreamService;
import com.filemngt.v2.scan.config.ScanSseProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Đồng bộ checkpoint lease và tín hiệu SSE sau khi persistence đã hoàn tất. */
@Component
final class ScanExecutionLiveness {
    private final ScanLeaseDeadlineGuard deadlineGuard;
    private final ScanRunStreamService streamService;
    private final ScanSseProperties sseProperties;

    ScanExecutionLiveness(
            ScanLeaseDeadlineGuard deadlineGuard,
            ScanRunStreamService streamService,
            ScanSseProperties sseProperties) {
        this.deadlineGuard = deadlineGuard;
        this.streamService = streamService;
        this.sseProperties = sseProperties;
    }

    void arm(UUID runId, String workerId, Instant leaseUntil) {
        deadlineGuard.arm(runId, workerId, leaseUntil);
    }

    void cancel(UUID runId) {
        deadlineGuard.cancel(runId);
    }

    void publishDurable(
            UUID runId,
            ScanRunStreamPhase phase,
            ScanChunkCommitter.ChunkProgress progress,
            Long changedFileCount,
            long reconciledFileCount) {
        streamService.publishProgress(ScanRunStreamProgress.durable(
                runId,
                phase,
                progress.files(),
                changedFileCount,
                reconciledFileCount,
                progress.proposals(),
                progress.issues()));
    }

    void publishTransient(ScanRunStreamProgress progress) {
        streamService.publishProgress(progress);
    }

    long progressIntervalMillis() {
        return sseProperties.getProgressIntervalMillis();
    }

    void publishTerminal(UUID runId) {
        streamService.publishTerminal(runId);
    }
}
