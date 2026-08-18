package com.filemngt.v2.scan.application.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ScanOutboxDrainCoordinatorTest {

    @Test
    void refillsWindowAfterCompletionWithoutWaitingForLegacyWaveDelay() {
        OutboxDrainProperties properties = properties();
        OutboxInFlightWindow window = new OutboxInFlightWindow(properties);
        ScanOutboxEventRepository events = mock(ScanOutboxEventRepository.class);
        ScanOutboxClaimService claims = mock(ScanOutboxClaimService.class);
        OutboxMessagePublisher messages = mock(OutboxMessagePublisher.class);
        ScanOutboxEventEntity first = event();
        ScanOutboxEventEntity second = event();
        ScanOutboxEventEntity third = event();
        ScanOutboxEventEntity fourth = event();
        when(claims.claim("coordinator-test", 2)).thenReturn(List.of(first, second), List.of(third, fourth), List.of());
        when(messages.publishAsync(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(events.markPublishedBatch(anyList(), eq("coordinator-test"), any()))
                .thenReturn(2);
        ScanOutboxMetrics metrics = new ScanOutboxMetrics(new SimpleMeterRegistry(), events, window);
        ScanOutboxDrainCoordinator coordinator = new ScanOutboxDrainCoordinator(
                claims,
                events,
                messages,
                window,
                properties,
                metrics,
                Tracer.NOOP,
                Propagator.NOOP,
                "coordinator-test");

        coordinator.drainTimeSlice();

        verify(claims, times(3)).claim("coordinator-test", 2);
        verify(events, times(2)).markPublishedBatch(anyList(), eq("coordinator-test"), any());
    }

    private OutboxDrainProperties properties() {
        OutboxDrainProperties properties = new OutboxDrainProperties();
        properties.setMaxInFlightEvents(2);
        properties.setClaimSize(2);
        properties.setDrainTimeSliceMs(100);
        properties.setIdleDelayMs(1);
        properties.setCompletionFlushSize(2);
        properties.setCompletionFlushIntervalMs(1);
        return properties;
    }

    private ScanOutboxEventEntity event() {
        return new ScanOutboxEventEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "media.file.discovered.v2",
                "CODE-001",
                "{}",
                "correlation-001",
                null,
                Instant.now());
    }
}
