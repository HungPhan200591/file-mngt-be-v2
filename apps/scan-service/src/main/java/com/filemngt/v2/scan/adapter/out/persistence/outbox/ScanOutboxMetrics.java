package com.filemngt.v2.scan.adapter.out.persistence.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ScanOutboxMetrics {
    private final Counter published;
    private final Counter failed;

    public ScanOutboxMetrics(MeterRegistry registry, ScanOutboxEventRepository events) {
        published = Counter.builder("scan.outbox.publish.success").register(registry);
        failed = Counter.builder("scan.outbox.publish.failure").register(registry);
        Gauge.builder("scan.outbox.pending", events, ScanOutboxEventRepository::countByPublishedAtIsNull)
                .register(registry);
        Gauge.builder("scan.outbox.oldest.pending.age", events, this::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .register(registry);
    }

    public void published() {
        published.increment();
    }

    public void failed() {
        failed.increment();
    }

    private double oldestPendingAgeSeconds(ScanOutboxEventRepository events) {
        return events.findFirstByPublishedAtIsNullOrderByCreatedAtAsc()
                .map(event -> Duration.between(event.createdAt(), Instant.now()).toSeconds())
                .orElse(0L);
    }
}
