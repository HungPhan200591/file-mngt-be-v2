package com.filemngt.v2.scan.application;

import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.adapter.out.persistence.ScanOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scan.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class ScanOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanOutboxPublisher.class);

    private final ScanOutboxEventRepository events;
    private final OutboxMessagePublisher messages;

    public ScanOutboxPublisher(ScanOutboxEventRepository events, OutboxMessagePublisher messages) {
        this.events = events;
        this.messages = messages;
    }

    @Scheduled(fixedDelayString = "${scan.outbox.fixed-delay-ms:1000}")
    public void publishPending() {
        for (var event : events.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                    event.correlationId(), event.traceparent())) {
                messages.publish(event.eventType(), event.partitionKey(), event.payload());
                event.published();
                events.save(event);
                LOGGER.info("Published outbox event eventId={} topic={}", event.id(), event.eventType());
            } catch (Exception exception) {
                event.failed(exception);
                events.save(event);
                LOGGER.warn(
                        "Outbox publish failed eventId={} attempt={} error={}",
                        event.id(),
                        event.attemptCount(),
                        exception.getMessage());
            }
        }
    }
}
