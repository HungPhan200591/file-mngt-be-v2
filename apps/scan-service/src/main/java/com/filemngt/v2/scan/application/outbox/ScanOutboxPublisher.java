package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
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
@ConditionalOnProperty(name = "scan.outbox.enabled", havingValue = "true", matchIfMissing = true)
/**
 * Publish transactional outbox theo lịch và chỉ cập nhật trạng thái sau từng lần broker phản hồi.
 * Batch bị giới hạn để một lần lỗi broker không giữ transaction hay memory quá lâu.
 */
public class ScanOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanOutboxPublisher.class);

    private final ScanOutboxEventRepository events;
    private final ScanOutboxClaimService claims;
    private final OutboxMessagePublisher messages;
    private final ScanOutboxMetrics metrics;
    private final Tracer tracer;
    private final Propagator propagator;
    private final String owner;
    private final int batchSize;

    @Autowired
    public ScanOutboxPublisher(
            ScanOutboxEventRepository events,
            ScanOutboxClaimService claims,
            OutboxMessagePublisher messages,
            ScanOutboxMetrics metrics,
            Tracer tracer,
            Propagator propagator,
            @Value("${scan.outbox.instance-id:${HOSTNAME:scan-publisher}}") String owner,
            @Value("${scan.outbox.batch-size:20}") int batchSize) {
        this.events = events;
        this.claims = claims;
        this.messages = messages;
        this.metrics = metrics;
        this.tracer = tracer;
        this.propagator = propagator;
        this.owner = owner;
        this.batchSize = batchSize;
    }

    /** Compatibility constructor for focused unit tests that predate leased claiming. */
    public ScanOutboxPublisher(
            ScanOutboxEventRepository events, OutboxMessagePublisher messages, Tracer tracer, Propagator propagator) {
        this.events = events;
        this.claims = null;
        this.messages = messages;
        this.metrics = null;
        this.tracer = tracer;
        this.propagator = propagator;
        this.owner = "legacy-test";
        this.batchSize = 20;
    }

    @Scheduled(fixedDelayString = "${scan.outbox.fixed-delay-ms:1000}")
    /** Publish các event chưa gửi và lưu một lần toàn bộ trạng thái publish/failure đã thay đổi. */
    public void publishPending() {
        var pendingEvents = claims == null
                ? events.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc()
                : claims.claim(owner, batchSize);
        if (pendingEvents.isEmpty()) {
            return;
        }
        // Publish per event is intentional: broker acknowledgement decides each outbox state.
        // The repository query bounds this loop to 20 events; persistence remains one bulk write.
        for (var event : pendingEvents) {
            try (var ignored = KafkaTracingHeaderPropagation.restoreOutboxTraceContext(
                    event.correlationId(), event.traceparent(), tracer, propagator)) {
                messages.publish(event.eventType(), event.partitionKey(), event.payload());
                if (claims == null) {
                    event.published();
                    events.save(event);
                } else {
                    events.markPublished(event.id(), owner, java.time.Instant.now());
                    metrics.published();
                }
                LOGGER.info("Published outbox event eventId={} topic={}", event.id(), event.eventType());
            } catch (Exception exception) {
                if (claims == null) {
                    event.failed(exception);
                    events.save(event);
                } else {
                    events.markFailed(event.id(), owner, errorMessage(exception));
                    metrics.failed();
                }
                LOGGER.warn(
                        "Outbox publish failed eventId={} attempt={} error={}",
                        event.id(),
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
