package com.filemngt.v2.scan.application.outbox.lane;

import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.lane.OutboxRelayLaneClaim;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.lane.OutboxRelayRecord;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.lane.ScanOutboxRelayLaneStore;
import com.filemngt.v2.scan.application.outbox.OutboxMessagePublisher;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scan.outbox.lane-relay-enabled", havingValue = "true")
/** Bounded worker pool lease lane ledger, không lease hoặc hydrate từng outbox event. */
public class ScanOutboxLaneRelayCoordinator {
    private final ScanOutboxRelayLaneStore store;
    private final OutboxMessagePublisher messages;
    private final OutboxDrainProperties properties;
    private final ScanOutboxMetrics metrics;
    private final Tracer tracer;
    private final Propagator propagator;
    private final String owner;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger nextLane = new AtomicInteger();

    public ScanOutboxLaneRelayCoordinator(
            ScanOutboxRelayLaneStore store,
            OutboxMessagePublisher messages,
            OutboxDrainProperties properties,
            ScanOutboxMetrics metrics,
            Tracer tracer,
            Propagator propagator,
            @Value("${scan.outbox.instance-id:${HOSTNAME:scan-publisher}}") String owner) {
        this.store = store;
        this.messages = messages;
        this.properties = properties;
        this.metrics = metrics;
        this.tracer = tracer;
        this.propagator = propagator;
        this.owner = owner;
    }

    public int drainTimeSlice() {
        if (!properties.isEnabled() || !properties.isLaneRelayEnabled()) return 0;
        var tasks = new ArrayList<CompletableFuture<Integer>>();
        int workerCount = Math.min(properties.getLaneWorkerConcurrency(), properties.getLaneCount());
        for (int worker = 0; worker < workerCount; worker++) {
            tasks.add(CompletableFuture.supplyAsync(() -> drainLane(nextLaneId()), workers));
        }
        return tasks.stream().mapToInt(CompletableFuture::join).sum();
    }

    @PreDestroy
    public void close() {
        workers.close();
    }

    private int drainLane(int laneId) {
        Instant now = Instant.now();
        var claim = store.acquire(laneId, owner, now, now.plusSeconds(properties.getLeaseSeconds()));
        if (claim.isEmpty()) return 0;
        List<OutboxRelayRecord> events = store.fetchPending(laneId, fetchLimit());
        if (events.isEmpty()) return 0;
        List<DeliveryResult> results = publish(events);
        int marked = markSuccessful(results, claim.get());
        markFailed(results, claim.get());
        return marked;
    }

    private int nextLaneId() {
        return Math.floorMod(nextLane.getAndIncrement(), properties.getLaneCount());
    }

    private int fetchLimit() {
        return Math.min(properties.getLaneFetchSize(), properties.getLaneMaxInFlightEvents());
    }

    private List<DeliveryResult> publish(List<OutboxRelayRecord> events) {
        List<CompletableFuture<DeliveryResult>> futures =
                events.stream().map(this::publish).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private CompletableFuture<DeliveryResult> publish(OutboxRelayRecord event) {
        Instant dispatchedAt = Instant.now();
        try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                event.correlationId(), event.traceparent(), tracer, propagator)) {
            return messages.publishAsync(event.eventType(), event.partitionKey(), event.payload())
                    .handle((unused, failure) -> deliveryResult(event.id(), dispatchedAt, failure))
                    .toCompletableFuture();
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(deliveryResult(event.id(), dispatchedAt, failure));
        }
    }

    private DeliveryResult deliveryResult(UUID eventId, Instant dispatchedAt, Throwable failure) {
        metrics.acknowledgement(Duration.between(dispatchedAt, Instant.now()));
        return new DeliveryResult(eventId, unwrap(failure));
    }

    private int markSuccessful(List<DeliveryResult> results, OutboxRelayLaneClaim claim) {
        List<UUID> successIds = results.stream()
                .filter(DeliveryResult::succeeded)
                .map(DeliveryResult::eventId)
                .toList();
        int marked = store.markPublished(successIds, claim, Instant.now());
        metrics.published(marked);
        for (int mismatch = marked; mismatch < successIds.size(); mismatch++) {
            metrics.leaseMismatch();
        }
        return marked;
    }

    private void markFailed(List<DeliveryResult> results, OutboxRelayLaneClaim claim) {
        results.stream().filter(result -> !result.succeeded()).forEach(result -> {
            int marked = store.markFailed(result.eventId(), claim, errorMessage(result.failure()), Instant.now());
            if (marked == 1) {
                metrics.failed();
            } else {
                metrics.leaseMismatch();
            }
        });
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof java.util.concurrent.CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return failure;
    }

    private static String errorMessage(Throwable failure) {
        if (failure == null) return "Unknown broker acknowledgement failure";
        String message = failure.getMessage();
        return message == null
                ? failure.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 2_000));
    }

    private record DeliveryResult(UUID eventId, Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }
}
