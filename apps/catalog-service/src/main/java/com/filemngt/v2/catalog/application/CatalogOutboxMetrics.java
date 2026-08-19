package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class CatalogOutboxMetrics {

    private final Counter publishSuccess;
    private final Counter publishFailure;
    private final Counter deadLetters;
    private final Counter leaseMismatch;
    private final Timer acknowledgement;

    public CatalogOutboxMetrics(MeterRegistry registry, CatalogOutboxEventRepository events) {
        publishSuccess = Counter.builder("catalog.outbox.publish.success").register(registry);
        publishFailure = Counter.builder("catalog.outbox.publish.failure").register(registry);
        deadLetters = Counter.builder("catalog.dead-letter.received").register(registry);
        leaseMismatch = Counter.builder("catalog.outbox.relay.lease.mismatch").register(registry);
        acknowledgement = Timer.builder("catalog.outbox.relay.acknowledgement").register(registry);
        Gauge.builder("catalog.outbox.pending", events, CatalogOutboxEventRepository::countByPublishedAtIsNull)
                .register(registry);
        Gauge.builder("catalog.outbox.oldest.pending.age", events, this::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .register(registry);
    }

    public void published() {
        publishSuccess.increment();
    }

    public void published(int count) {
        publishSuccess.increment(count);
    }

    public void publishFailed() {
        publishFailure.increment();
    }

    public void leaseMismatch(int count) {
        leaseMismatch.increment(count);
    }

    public void acknowledgement(Duration duration) {
        acknowledgement.record(duration);
    }

    public void deadLetterReceived() {
        deadLetters.increment();
    }

    private double oldestPendingAgeSeconds(CatalogOutboxEventRepository events) {
        return events.findFirstByPublishedAtIsNullOrderByCreatedAtAsc()
                .map(event -> Duration.between(event.createdAt(), Instant.now()).toSeconds())
                .orElse(0L);
    }
}
