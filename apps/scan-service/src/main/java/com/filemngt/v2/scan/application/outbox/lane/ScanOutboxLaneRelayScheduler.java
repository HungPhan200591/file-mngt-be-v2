package com.filemngt.v2.scan.application.outbox.lane;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scan.outbox.lane-relay-enabled", havingValue = "true")
/** Chỉ đánh thức bounded lane workers; lane ownership và native persistence thuộc coordinator/store. */
public class ScanOutboxLaneRelayScheduler {
    private final ScanOutboxLaneRelayCoordinator coordinator;

    /**
     * Adaptive backoff: khi drain trả 0 (không có pending events trong bất kỳ lane nào),
     * tăng dần khoảng nghỉ để tránh spam hàng nghìn empty SQL queries/giây vào PostgreSQL.
     * Reset về 0 ngay khi drain được ít nhất 1 event.
     */
    private static final long BACKOFF_MAX_MS = 500;

    private static final long BACKOFF_STEP_MS = 10;

    private long currentBackoffMs = 0;

    public ScanOutboxLaneRelayScheduler(ScanOutboxLaneRelayCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${scan.outbox.scheduler-delay-ms:1}")
    public void drain() {
        if (currentBackoffMs > 0) {
            try {
                Thread.sleep(currentBackoffMs);
            } catch (InterruptedException interrupt) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        int drained = coordinator.drainTimeSlice();
        if (drained > 0) {
            currentBackoffMs = 0;
        } else {
            currentBackoffMs = Math.min(currentBackoffMs + BACKOFF_STEP_MS, BACKOFF_MAX_MS);
        }
    }
}
