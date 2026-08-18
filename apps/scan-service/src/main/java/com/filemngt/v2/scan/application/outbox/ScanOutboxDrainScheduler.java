package com.filemngt.v2.scan.application.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${scan.outbox.continuous-drain-enabled:true} and !${scan.outbox.lane-relay-enabled:false}")
/** Scheduler chỉ đánh thức coordinator; refill và wait thuộc coordinator để giữ lifecycle quan sát được. */
public class ScanOutboxDrainScheduler {
    private final ScanOutboxDrainCoordinator coordinator;

    public ScanOutboxDrainScheduler(ScanOutboxDrainCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${scan.outbox.scheduler-delay-ms:1}")
    public void drain() {
        coordinator.drainTimeSlice();
    }
}
