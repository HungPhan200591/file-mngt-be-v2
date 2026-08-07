package com.filemngt.v2.scan.application.scan.deadline;

import com.filemngt.v2.scan.application.scan.ScanChunkCommitter;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
/** Re-arm one-shot deadline theo checkpoint; handle RAM chỉ tối ưu, PostgreSQL là authority. */
public class ScanLeaseDeadlineGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanLeaseDeadlineGuard.class);

    private final TaskScheduler scheduler;
    private final ScanLeaseExpiryHandler expiryHandler;
    private final ScanChunkCommitter chunkCommitter;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> deadlines = new ConcurrentHashMap<>();

    public ScanLeaseDeadlineGuard(
            @Qualifier("scanLeaseDeadlineScheduler") TaskScheduler scheduler,
            ScanLeaseExpiryHandler expiryHandler,
            ScanChunkCommitter chunkCommitter) {
        this.scheduler = scheduler;
        this.expiryHandler = expiryHandler;
        this.chunkCommitter = chunkCommitter;
    }

    public void arm(UUID runId, String workerId, Instant leaseUntil) {
        deadlines.compute(runId, (ignored, existing) -> schedule(runId, workerId, leaseUntil, existing));
    }

    public void cancel(UUID runId) {
        ScheduledFuture<?> future = deadlines.remove(runId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private ScheduledFuture<?> schedule(
            UUID runId, String workerId, Instant leaseUntil, ScheduledFuture<?> existing) {
        if (existing != null) {
            existing.cancel(false);
        }
        return scheduler.schedule(() -> expire(runId, workerId), leaseUntil);
    }

    private void expire(UUID runId, String workerId) {
        try {
            if (expiryHandler.expire(runId, workerId)) {
                cancel(runId);
                chunkCommitter.cleanupStage(runId);
                LOGGER.warn("Đã đóng scan run do lease hết hạn: runId={}, workerId={}", runId, workerId);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Không thể xử lý deadline scan run: runId={}, workerId={}", runId, workerId, exception);
        }
    }
}
