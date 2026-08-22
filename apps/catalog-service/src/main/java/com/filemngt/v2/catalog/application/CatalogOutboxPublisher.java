package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventRepository;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.outbox.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "catalog.outbox.operation-relay.enabled", havingValue = "false", matchIfMissing = true)
public class CatalogOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOutboxPublisher.class);

    private final CatalogOutboxEventRepository events;
    private final CatalogOutboxClaimService claims;
    private final CatalogOutboxMessagePublisher messages;
    private final CatalogOutboxMetrics metrics;
    private final Tracer tracer;
    private final Propagator propagator;
    private final String owner;
    private final int batchSize;

    @Autowired
    public CatalogOutboxPublisher(
            CatalogOutboxEventRepository events,
            CatalogOutboxClaimService claims,
            CatalogOutboxMessagePublisher messages,
            CatalogOutboxMetrics metrics,
            Tracer tracer,
            Propagator propagator,
            @Value("${catalog.outbox.instance-id:${HOSTNAME:catalog-publisher}}") String owner,
            @Value("${catalog.outbox.batch-size:20}") int batchSize) {
        this.events = events;
        this.claims = claims;
        this.messages = messages;
        this.metrics = metrics;
        this.tracer = tracer;
        this.propagator = propagator;
        this.owner = owner;
        this.batchSize = batchSize;
    }

    /** Compatibility constructor for focused integration tests that predate leased claiming. */
    public CatalogOutboxPublisher(
            CatalogOutboxEventRepository events,
            CatalogOutboxMessagePublisher messages,
            CatalogOutboxMetrics metrics,
            Tracer tracer,
            Propagator propagator) {
        this.events = events;
        this.claims = null;
        this.messages = messages;
        this.metrics = metrics;
        this.tracer = tracer;
        this.propagator = propagator;
        this.owner = "legacy-test";
        this.batchSize = 20;
    }

    @Scheduled(fixedDelayString = "${catalog.outbox.fixed-delay-ms:1000}")
    public void publishPending() {
        var pendingEvents = claims == null
                ? events.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc()
                : claims.claim(owner, batchSize);
        if (pendingEvents.isEmpty()) return;
        var dispatched = pendingEvents.stream().map(this::dispatch).toList();
        var publishedIds = new java.util.ArrayList<java.util.UUID>(dispatched.size());
        for (var item : dispatched) {
            var event = item.event();
            try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                    event.correlationId(), event.traceparent(), tracer, propagator)) {
                item.acknowledgement().toCompletableFuture().join();
                if (claims == null) {
                    event.published();
                    events.save(event);
                } else {
                    publishedIds.add(event.id());
                }
                if (claims == null) metrics.published();
                LOGGER.debug(
                        "Published catalog outbox event eventId={} subjectId={} version={} topic={}",
                        event.id(),
                        event.subjectId(),
                        event.subjectVersion(),
                        event.eventType());
            } catch (Exception exception) {
                if (claims == null) {
                    event.failed(exception);
                    events.save(event);
                } else {
                    events.markFailed(event.id(), owner, errorMessage(exception));
                }
                metrics.publishFailed();
                LOGGER.warn(
                        "Catalog outbox publish failed eventId={} subjectId={} version={} attempt={} error={}",
                        event.id(),
                        event.subjectId(),
                        event.subjectVersion(),
                        event.attemptCount(),
                        exception.getMessage());
            }
        }
        if (!publishedIds.isEmpty()) {
            int persisted = events.markPublishedBatch(publishedIds, owner, java.time.Instant.now());
            for (int index = 0; index < persisted; index++) metrics.published();
        }
        LOGGER.info("Published Catalog outbox batch claimed={} succeeded={}", dispatched.size(), publishedIds.size());
    }

    private DispatchedEvent dispatch(com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventEntity event) {
        try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                event.correlationId(), event.traceparent(), tracer, propagator)) {
            java.util.concurrent.CompletionStage<Void> acknowledgement;
            if (claims == null) {
                messages.publish(event.eventType(), event.partitionKey(), event.payload());
                acknowledgement = java.util.concurrent.CompletableFuture.completedFuture(null);
            } else {
                acknowledgement = messages.publishAsync(event.eventType(), event.partitionKey(), event.payload());
            }
            return new DispatchedEvent(event, acknowledgement);
        } catch (Exception exception) {
            return new DispatchedEvent(event, java.util.concurrent.CompletableFuture.failedFuture(exception));
        }
    }

    private record DispatchedEvent(
            com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventEntity event,
            java.util.concurrent.CompletionStage<Void> acknowledgement) {}

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 2000));
    }
}
