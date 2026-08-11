package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventRepository;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@ConditionalOnProperty(name = "catalog.outbox.enabled", havingValue = "true", matchIfMissing = true)
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
        for (var event : pendingEvents) {
            try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                    event.correlationId(), event.traceparent(), tracer, propagator)) {
                messages.publish(event.eventType(), event.partitionKey(), event.payload());
                if (claims == null) {
                    event.published();
                    events.save(event);
                } else {
                    events.markPublished(event.id(), owner, java.time.Instant.now());
                }
                metrics.published();
                LOGGER.info(
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
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 2000));
    }
}
