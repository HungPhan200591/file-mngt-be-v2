package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
/** Điều phối bounded sliding window; Kafka callback chỉ ghi completion, DB mark chạy tại scheduler thread. */
public class ScanOutboxDrainCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanOutboxDrainCoordinator.class);

    private final ScanOutboxClaimService claims;
    private final ScanOutboxEventRepository events;
    private final OutboxMessagePublisher messages;
    private final OutboxInFlightWindow window;
    private final OutboxDrainProperties properties;
    private final ScanOutboxMetrics metrics;
    private final Tracer tracer;
    private final Propagator propagator;
    private final String owner;
    private final AtomicBoolean breakerOpen = new AtomicBoolean();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private Instant lastCompletionFlushAt = Instant.EPOCH;

    public ScanOutboxDrainCoordinator(
            ScanOutboxClaimService claims,
            ScanOutboxEventRepository events,
            OutboxMessagePublisher messages,
            OutboxInFlightWindow window,
            OutboxDrainProperties properties,
            ScanOutboxMetrics metrics,
            Tracer tracer,
            Propagator propagator,
            @org.springframework.beans.factory.annotation.Value("${scan.outbox.instance-id:${HOSTNAME:scan-publisher}}")
                    String owner) {
        this.claims = claims;
        this.events = events;
        this.messages = messages;
        this.window = window;
        this.properties = properties;
        this.metrics = metrics;
        this.tracer = tracer;
        this.propagator = propagator;
        this.owner = owner;
    }

    public void drainTimeSlice() {
        if (!properties.isEnabled() || shuttingDown.get() || breakerOpen.get()) {
            return;
        }
        var cycle = metrics.startDrainCycle();
        try {
            Instant deadline = Instant.now().plusMillis(properties.getDrainTimeSliceMs());
            while (!shuttingDown.get() && !breakerOpen.get() && Instant.now().isBefore(deadline)) {
                flushCompletions(false);
                int freeSlots = window.freeSlots();
                if (freeSlots > 0) {
                    int limit = Math.min(freeSlots, properties.getClaimSize());
                    var claimed = claims.claim(owner, limit);
                    if (!claimed.isEmpty()) {
                        dispatch(claimed);
                        continue;
                    }
                    if (window.occupied() == 0) {
                        idleBackoff();
                        return;
                    }
                }
                awaitCompletion(deadline);
            }
            flushCompletions(false);
        } finally {
            metrics.stopDrainCycle(cycle);
        }
    }

    @PreDestroy
    void shutdown() {
        shuttingDown.set(true);
        Instant deadline = Instant.now().plusMillis(properties.getShutdownGraceMs());
        while (window.completionDepth() > 0 && Instant.now().isBefore(deadline)) {
            flushCompletions(true);
        }
    }

    private void dispatch(List<ScanOutboxEventEntity> claimed) {
        window.reserve(claimed.size());
        for (ScanOutboxEventEntity event : claimed) {
            Instant dispatchedAt = Instant.now();
            try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                    event.correlationId(), event.traceparent(), tracer, propagator)) {
                messages.publishAsync(event.eventType(), event.partitionKey(), event.payload())
                        .whenComplete((ignoredResult, failure) -> {
                            metrics.acknowledgement(Duration.between(dispatchedAt, Instant.now()));
                            window.complete(new OutboxCompletion(event.id(), Instant.now(), unwrap(failure)));
                        });
            } catch (RuntimeException failure) {
                metrics.acknowledgement(Duration.between(dispatchedAt, Instant.now()));
                window.complete(new OutboxCompletion(event.id(), Instant.now(), failure));
            }
        }
    }

    private void flushCompletions(boolean force) {
        if (!force
                && window.completionDepth() < properties.getCompletionFlushSize()
                && Duration.between(lastCompletionFlushAt, Instant.now()).toMillis()
                        < properties.getCompletionFlushIntervalMs()) {
            return;
        }
        List<OutboxCompletion> completions = window.drain(properties.getCompletionFlushSize());
        if (completions.isEmpty()) {
            return;
        }
        lastCompletionFlushAt = Instant.now();
        var successIds = completions.stream()
                .filter(OutboxCompletion::succeeded)
                .map(OutboxCompletion::eventId)
                .toList();
        if (!successIds.isEmpty()) {
            int persisted = events.markPublishedBatch(successIds, owner, Instant.now());
            metrics.published(persisted);
            recordOwnerMismatch(successIds.size() - persisted);
        }
        completions.stream().filter(completion -> !completion.succeeded()).forEach(this::markFailure);
        window.release(completions.size());
    }

    private void markFailure(OutboxCompletion completion) {
        int persisted = events.markFailed(completion.eventId(), owner, errorMessage(completion.failure()));
        if (persisted == 0) {
            metrics.leaseMismatch();
        } else {
            metrics.failed();
        }
        breakerOpen.set(true);
        metrics.breakerOpen(true);
        LOGGER.warn("Outbox breaker opened after broker acknowledgement failure eventId={}", completion.eventId());
    }

    private void recordOwnerMismatch(int count) {
        for (int index = 0; index < count; index++) {
            metrics.leaseMismatch();
        }
    }

    private void awaitCompletion(Instant deadline) {
        long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
        if (remainingMillis <= 0) {
            return;
        }
        long waitMillis = Math.min(properties.acknowledgementDeadlineMs(), remainingMillis);
        try {
            window.awaitCompletion(Duration.ofMillis(Math.max(1, waitMillis)));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            shuttingDown.set(true);
        }
    }

    private void idleBackoff() {
        try {
            Thread.sleep(properties.getIdleDelayMs());
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            shuttingDown.set(true);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure == null) {
            return null;
        }
        if (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    private static String errorMessage(Throwable failure) {
        String message = failure == null ? "Unknown broker acknowledgement failure" : failure.getMessage();
        return message == null
                ? failure.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 2_000));
    }
}
