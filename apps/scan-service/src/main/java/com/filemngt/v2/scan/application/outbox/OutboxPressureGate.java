package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxMetrics;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import com.filemngt.v2.scan.config.OutboxPressureProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
/** Hysteresis gate chỉ pause bulk approval claim; interactive decision vẫn tạo outbox bình thường. */
public class OutboxPressureGate {
    private final OutboxDrainProperties properties;
    private final OutboxPressureProperties pressure;
    private final ScanOutboxEventRepository events;
    private final OutboxInFlightWindow window;
    private final ScanOutboxMetrics metrics;
    private Instant lastSampleAt = Instant.EPOCH;
    private Instant healthySince;
    private boolean paused;

    public OutboxPressureGate(
            OutboxDrainProperties properties,
            OutboxPressureProperties pressure,
            ScanOutboxEventRepository events,
            OutboxInFlightWindow window,
            ScanOutboxMetrics metrics) {
        this.properties = properties;
        this.pressure = pressure;
        this.events = events;
        this.window = window;
        this.metrics = metrics;
    }

    public synchronized boolean allowBulkClaim() {
        if (!properties.isEnabled() || !properties.isContinuousDrainEnabled() || !pressure.isEnabled()) {
            return true;
        }
        Instant now = Instant.now();
        if (Duration.between(lastSampleAt, now).toMillis() >= pressure.getSampleIntervalMs()) {
            sample(now);
        }
        return !paused;
    }

    private void sample(Instant now) {
        lastSampleAt = now;
        long oldestAgeMillis = events.findFirstByPublishedAtIsNullOrderByCreatedAtAsc()
                .map(event ->
                        Math.max(0, Duration.between(event.createdAt(), now).toMillis()))
                .orElse(0L);
        int inFlight = window.occupied();
        if (!paused
                && (oldestAgeMillis >= pressure.getHighPendingAgeMs()
                        || inFlight >= pressure.getHighInFlightEvents())) {
            paused = true;
            healthySince = null;
            metrics.pressurePaused(true);
            return;
        }
        if (!paused) {
            return;
        }
        boolean healthy =
                oldestAgeMillis <= pressure.getLowPendingAgeMs() && inFlight <= pressure.getLowInFlightEvents();
        if (!healthy) {
            healthySince = null;
            return;
        }
        if (healthySince == null) {
            healthySince = now;
            return;
        }
        if (Duration.between(healthySince, now).toMillis() >= pressure.getStableWindowMs()) {
            paused = false;
            healthySince = null;
            metrics.pressurePaused(false);
        }
    }
}
