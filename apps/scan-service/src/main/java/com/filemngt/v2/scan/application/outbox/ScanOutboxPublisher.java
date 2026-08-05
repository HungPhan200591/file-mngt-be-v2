package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scan.outbox.enabled", havingValue = "true", matchIfMissing = true)
/**
 * Publish transactional outbox theo lịch và chỉ cập nhật trạng thái sau từng lần broker phản hồi.
 * Batch bị giới hạn để một lần lỗi broker không giữ transaction hay memory quá lâu.
 */
public class ScanOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanOutboxPublisher.class);

    private final ScanOutboxEventRepository events;
    private final OutboxMessagePublisher messages;
    private final Tracer tracer;
    private final Propagator propagator;

    public ScanOutboxPublisher(
            ScanOutboxEventRepository events, OutboxMessagePublisher messages, Tracer tracer, Propagator propagator) {
        this.events = events;
        this.messages = messages;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Scheduled(fixedDelayString = "${scan.outbox.fixed-delay-ms:1000}")
    /** Publish các event chưa gửi và lưu một lần toàn bộ trạng thái publish/failure đã thay đổi. */
    public void publishPending() {
        var pendingEvents = events.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return;
        }
        // Publish per event is intentional: broker acknowledgement decides each outbox state.
        // The repository query bounds this loop to 20 events; persistence remains one bulk write.
        for (var event : pendingEvents) {
            try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                    event.correlationId(), event.traceparent(), tracer, propagator)) {
                messages.publish(event.eventType(), event.partitionKey(), event.payload());
                event.published();
                LOGGER.info("Published outbox event eventId={} topic={}", event.id(), event.eventType());
            } catch (Exception exception) {
                event.failed(exception);
                LOGGER.warn(
                        "Outbox publish failed eventId={} attempt={} error={}",
                        event.id(),
                        event.attemptCount(),
                        exception.getMessage());
            }
        }
        events.saveAll(pendingEvents);
    }
}
