package com.filemngt.v2.catalog.application.outbox.operation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.outbox.operation-relay.enabled", havingValue = "true")
public class CatalogOutboxRelayScheduler {
    private final CatalogOutboxRelayCoordinator coordinator;

    public CatalogOutboxRelayScheduler(CatalogOutboxRelayCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${catalog.outbox.operation-relay.scheduler-delay-ms:1}")
    public void drain() {
        coordinator.drainTimeSlice();
    }
}
