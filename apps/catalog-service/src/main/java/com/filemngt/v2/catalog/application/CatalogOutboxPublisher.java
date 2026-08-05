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

@Component
@ConditionalOnProperty(name = "catalog.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOutboxPublisher.class);

    private final CatalogOutboxEventRepository events;
    private final CatalogOutboxMessagePublisher messages;
    private final CatalogOutboxMetrics metrics;
    private final Tracer tracer;
    private final Propagator propagator;

    public CatalogOutboxPublisher(
            CatalogOutboxEventRepository events,
            CatalogOutboxMessagePublisher messages,
            CatalogOutboxMetrics metrics,
            Tracer tracer,
            Propagator propagator) {
        this.events = events;
        this.messages = messages;
        this.metrics = metrics;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Scheduled(fixedDelayString = "${catalog.outbox.fixed-delay-ms:1000}")
    public void publishPending() {
        for (var event : events.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                    event.correlationId(), event.traceparent(), tracer, propagator)) {
                messages.publish(event.eventType(), event.partitionKey(), event.payload());
                event.published();
                events.save(event);
                metrics.published();
                LOGGER.info(
                        "Published catalog outbox event eventId={} subjectId={} version={} topic={}",
                        event.id(),
                        event.subjectId(),
                        event.subjectVersion(),
                        event.eventType());
            } catch (Exception exception) {
                event.failed(exception);
                events.save(event);
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
}
