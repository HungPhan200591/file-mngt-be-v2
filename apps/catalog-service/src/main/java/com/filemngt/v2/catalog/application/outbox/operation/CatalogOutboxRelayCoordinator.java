package com.filemngt.v2.catalog.application.outbox.operation;

import com.filemngt.v2.catalog.adapter.out.persistence.outbox.operation.CatalogOutboxRelayLaneClaim;
import com.filemngt.v2.catalog.adapter.out.persistence.outbox.operation.CatalogOutboxRelayLaneStore;
import com.filemngt.v2.catalog.adapter.out.persistence.outbox.operation.CatalogOutboxRelayRecord;
import com.filemngt.v2.catalog.application.CatalogOutboxMessagePublisher;
import com.filemngt.v2.catalog.application.CatalogOutboxMetrics;
import com.filemngt.v2.catalog.config.CatalogOutboxRelayProperties;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.outbox.operation-relay.enabled", havingValue = "true")
/** Async dispatch ngoài DB transaction, bounded theo worker × fetch size và fenced khi durable mark. */
public class CatalogOutboxRelayCoordinator {
    private static final long KAFKA_ACK_TIMEOUT_SECONDS = 5;
    private static final long LEASE_SAFETY_SECONDS = 1;
    private final CatalogOutboxRelayLaneStore store;
    private final CatalogOutboxMessagePublisher messages;
    private final CatalogOutboxRelayProperties properties;
    private final CatalogOutboxMetrics metrics;
    private final Tracer tracer;
    private final Propagator propagator;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger nextLane = new AtomicInteger();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong backoffUntilNanos = new AtomicLong();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public CatalogOutboxRelayCoordinator(
            CatalogOutboxRelayLaneStore store,
            CatalogOutboxMessagePublisher messages,
            CatalogOutboxRelayProperties properties,
            CatalogOutboxMetrics metrics,
            Tracer tracer,
            Propagator propagator) {
        this.store = store;
        this.messages = messages;
        this.properties = properties;
        this.metrics = metrics;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public int drainTimeSlice() {
        if (!accepting.get() || System.nanoTime() < backoffUntilNanos.get()) return 0;
        int workerCount = Math.min(properties.getWorkerCount(), properties.getLaneCount());
        List<CompletableFuture<Integer>> tasks = new ArrayList<>(workerCount);
        for (int worker = 0; worker < workerCount; worker++) {
            tasks.add(CompletableFuture.supplyAsync(() -> drainLane(nextLaneId()), workers));
        }
        int published = tasks.stream().mapToInt(CompletableFuture::join).sum();
        if (published == 0 && consecutiveFailures.get() == 0) pause(properties.getIdleBackoffMillis());
        if (published > 0) consecutiveFailures.set(0);
        return published;
    }

    @PreDestroy
    public void close() {
        accepting.set(false);
        workers.close();
    }

    private int drainLane(int laneId) {
        Instant now = Instant.now();
        var claim =
                store.acquire(laneId, properties.getInstanceId(), now, now.plusSeconds(properties.getLeaseSeconds()));
        if (claim.isEmpty()) return 0;
        try {
            List<CatalogOutboxRelayRecord> events = store.fetchPending(laneId, safeFetchLimit());
            if (events.isEmpty()) return 0;
            return publishWithSlidingWindow(events, claim.get());
        } finally {
            store.release(claim.get());
        }
    }

    private int nextLaneId() {
        return Math.floorMod(nextLane.getAndIncrement(), properties.getLaneCount());
    }

    /** Không dispatch quá số wave có thể hết trước Kafka ack timeout và lease safety margin. */
    private int safeFetchLimit() {
        long waves = Math.max(1, (properties.getLeaseSeconds() - LEASE_SAFETY_SECONDS) / KAFKA_ACK_TIMEOUT_SECONDS);
        long bounded = Math.min((long) properties.getFetchSize(), properties.getMaxInFlight() * waves);
        return Math.toIntExact(Math.max(1, bounded));
    }

    /**
     * Không đợi slowest acknowledgement của cả fetch. Cửa sổ bounded được refill khi bất kỳ send nào hoàn tất,
     * rồi durable mark ngay để retry chỉ còn phần chưa ack.
     */
    private int publishWithSlidingWindow(List<CatalogOutboxRelayRecord> events, CatalogOutboxRelayLaneClaim claim) {
        Iterator<CatalogOutboxRelayRecord> pending = events.iterator();
        List<CompletableFuture<DeliveryResult>> inFlight = new ArrayList<>(properties.getMaxInFlight());
        int published = 0;

        while (pending.hasNext() || !inFlight.isEmpty()) {
            while (pending.hasNext() && inFlight.size() < properties.getMaxInFlight()) {
                inFlight.add(publish(pending.next()));
            }

            CompletableFuture.anyOf(inFlight.toArray(CompletableFuture[]::new)).join();
            List<DeliveryResult> completed = new ArrayList<>();
            for (Iterator<CompletableFuture<DeliveryResult>> iterator = inFlight.iterator(); iterator.hasNext(); ) {
                CompletableFuture<DeliveryResult> delivery = iterator.next();
                if (delivery.isDone()) {
                    completed.add(delivery.join());
                    iterator.remove();
                }
            }
            published += markSuccessful(completed, claim);
            markFailed(completed, claim);
        }
        return published;
    }

    private CompletableFuture<DeliveryResult> publish(CatalogOutboxRelayRecord event) {
        Instant dispatchedAt = Instant.now();
        try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                event.correlationId(), event.traceparent(), tracer, propagator)) {
            return messages.publishAsync(event.eventType(), event.partitionKey(), event.payload())
                    .handle((unused, failure) -> deliveryResult(event.id(), dispatchedAt, failure))
                    .toCompletableFuture();
        } catch (Exception failure) {
            return CompletableFuture.completedFuture(deliveryResult(event.id(), dispatchedAt, failure));
        }
    }

    private DeliveryResult deliveryResult(UUID eventId, Instant dispatchedAt, Throwable failure) {
        metrics.acknowledgement(Duration.between(dispatchedAt, Instant.now()));
        return new DeliveryResult(eventId, unwrap(failure));
    }

    private int markSuccessful(List<DeliveryResult> results, CatalogOutboxRelayLaneClaim claim) {
        List<UUID> ids = results.stream()
                .filter(DeliveryResult::succeeded)
                .map(DeliveryResult::eventId)
                .toList();
        int marked = store.markPublished(ids, claim, Instant.now());
        metrics.published(marked);
        metrics.leaseMismatch(ids.size() - marked);
        return marked;
    }

    private void markFailed(List<DeliveryResult> results, CatalogOutboxRelayLaneClaim claim) {
        List<DeliveryResult> failures =
                results.stream().filter(result -> !result.succeeded()).toList();
        failures.forEach(result -> {
            int marked = store.markFailed(result.eventId(), claim, errorMessage(result.failure()), Instant.now());
            if (marked == 1) metrics.publishFailed();
            else metrics.leaseMismatch(1);
        });
        if (!failures.isEmpty()) pause(failureBackoffMillis());
    }

    private long failureBackoffMillis() {
        int exponent = Math.min(consecutiveFailures.getAndIncrement(), 10);
        long backoff = properties.getIdleBackoffMillis() << exponent;
        return Math.min(backoff, properties.getMaximumFailureBackoffMillis());
    }

    private void pause(long millis) {
        long candidate = System.nanoTime() + Duration.ofMillis(millis).toNanos();
        backoffUntilNanos.accumulateAndGet(candidate, Math::max);
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
