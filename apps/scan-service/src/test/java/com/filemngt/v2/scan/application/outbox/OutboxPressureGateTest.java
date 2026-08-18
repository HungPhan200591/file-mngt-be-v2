package com.filemngt.v2.scan.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import com.filemngt.v2.scan.config.OutboxPressureProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxPressureGateTest {

    @Test
    void pausesBulkClaimThenResumesAfterLowWatermarkIsStable() {
        OutboxDrainProperties properties = new OutboxDrainProperties();
        OutboxPressureProperties pressure = new OutboxPressureProperties();
        pressure.setSampleIntervalMs(0);
        pressure.setStableWindowMs(0);
        pressure.setHighPendingAgeMs(1);
        pressure.setLowPendingAgeMs(0);
        ScanOutboxEventRepository events = mock(ScanOutboxEventRepository.class);
        ScanOutboxEventEntity oldEvent = event(Instant.now().minusSeconds(10));
        when(events.findFirstByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Optional.of(oldEvent), Optional.empty(), Optional.empty());
        OutboxInFlightWindow window = new OutboxInFlightWindow(properties);
        ScanOutboxMetrics metrics = new ScanOutboxMetrics(new SimpleMeterRegistry(), events, window);
        OutboxPressureGate gate = new OutboxPressureGate(properties, pressure, events, window, metrics);

        assertThat(gate.allowBulkClaim()).isFalse();
        assertThat(gate.allowBulkClaim()).isFalse();
        assertThat(gate.allowBulkClaim()).isTrue();
    }

    private ScanOutboxEventEntity event(Instant createdAt) {
        return new ScanOutboxEventEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "media.file.discovered.v2",
                "CODE-001",
                "{}",
                "corr",
                null,
                createdAt);
    }
}
