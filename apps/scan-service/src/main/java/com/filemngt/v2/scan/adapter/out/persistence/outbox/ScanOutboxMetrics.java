package com.filemngt.v2.scan.adapter.out.persistence.outbox;

import com.filemngt.v2.scan.application.outbox.OutboxInFlightWindow;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class ScanOutboxMetrics {
    private final Counter published;
    private final Counter failed;
    private final Counter leaseMismatch;
    private final Timer acknowledgement;
    private final Timer drainCycle;
    private final AtomicBoolean pressurePaused = new AtomicBoolean();
    private final AtomicBoolean breakerOpen = new AtomicBoolean();

    public ScanOutboxMetrics(
            MeterRegistry registry, ScanOutboxEventRepository events, OutboxInFlightWindow inFlightWindow) {
        published = Counter.builder("scan.outbox.publish.success").register(registry);
        failed = Counter.builder("scan.outbox.publish.failure").register(registry);
        leaseMismatch =
                Counter.builder("scan.outbox.conditional-mark.owner-mismatch").register(registry);
        acknowledgement = Timer.builder("scan.outbox.broker.acknowledgement").register(registry);
        drainCycle = Timer.builder("scan.outbox.drain.cycle").register(registry);
        Gauge.builder("scan.outbox.pending", events, ScanOutboxEventRepository::countByPublishedAtIsNull)
                .register(registry);
        Gauge.builder("scan.outbox.oldest.pending.age", events, this::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder("scan.outbox.in-flight", inFlightWindow, OutboxInFlightWindow::occupied)
                .register(registry);
        Gauge.builder("scan.outbox.in-flight.max", inFlightWindow, OutboxInFlightWindow::maximum)
                .register(registry);
        Gauge.builder("scan.outbox.completion.queue.depth", inFlightWindow, OutboxInFlightWindow::completionDepth)
                .register(registry);
        Gauge.builder("scan.outbox.pressure.paused", pressurePaused, value -> value.get() ? 1 : 0)
                .register(registry);
        Gauge.builder("scan.outbox.breaker.open", breakerOpen, value -> value.get() ? 1 : 0)
                .register(registry);
    }

    public void published() {
        published.increment();
    }

    public void published(int count) {
        published.increment(count);
    }

    public void failed() {
        failed.increment();
    }

    public void leaseMismatch() {
        leaseMismatch.increment();
    }

    public void acknowledgement(Duration duration) {
        acknowledgement.record(duration);
    }

    public Timer.Sample startDrainCycle() {
        return Timer.start();
    }

    public void stopDrainCycle(Timer.Sample sample) {
        sample.stop(drainCycle);
    }

    public void pressurePaused(boolean paused) {
        pressurePaused.set(paused);
    }

    public void breakerOpen(boolean open) {
        breakerOpen.set(open);
    }

    private double oldestPendingAgeSeconds(ScanOutboxEventRepository events) {
        return events.findFirstByPublishedAtIsNullOrderByCreatedAtAsc()
                .map(event -> Duration.between(event.createdAt(), Instant.now()).toSeconds())
                .orElse(0L);
    }
}
