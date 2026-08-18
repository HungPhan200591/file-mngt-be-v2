package com.filemngt.v2.scan.application.outbox.lane;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scan.outbox.lane-relay-enabled", havingValue = "true")
/** Chỉ đánh thức bounded lane workers; lane ownership và native persistence thuộc coordinator/store. */
public class ScanOutboxLaneRelayScheduler {
    private final ScanOutboxLaneRelayCoordinator coordinator;

    public ScanOutboxLaneRelayScheduler(ScanOutboxLaneRelayCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${scan.outbox.scheduler-delay-ms:1}")
    public void drain() {
        coordinator.drainTimeSlice();
    }
}
