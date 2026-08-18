package com.filemngt.v2.scan.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "scan.outbox.pressure")
@Validated
/** Hysteresis thresholds chỉ điều tiết bulk approval intake, không chặn interactive decision. */
public class OutboxPressureProperties {
    private boolean enabled = true;

    @Min(1)
    private long sampleIntervalMs = 1_000;

    @Min(0)
    private long highPendingAgeMs = 30_000;

    @Min(0)
    private long lowPendingAgeMs = 10_000;

    @Min(0)
    private int highInFlightEvents = 450;

    @Min(0)
    private int lowInFlightEvents = 250;

    @Min(0)
    private long stableWindowMs = 5_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSampleIntervalMs() {
        return sampleIntervalMs;
    }

    public void setSampleIntervalMs(long sampleIntervalMs) {
        this.sampleIntervalMs = sampleIntervalMs;
    }

    public long getHighPendingAgeMs() {
        return highPendingAgeMs;
    }

    public void setHighPendingAgeMs(long highPendingAgeMs) {
        this.highPendingAgeMs = highPendingAgeMs;
    }

    public long getLowPendingAgeMs() {
        return lowPendingAgeMs;
    }

    public void setLowPendingAgeMs(long lowPendingAgeMs) {
        this.lowPendingAgeMs = lowPendingAgeMs;
    }

    public int getHighInFlightEvents() {
        return highInFlightEvents;
    }

    public void setHighInFlightEvents(int highInFlightEvents) {
        this.highInFlightEvents = highInFlightEvents;
    }

    public int getLowInFlightEvents() {
        return lowInFlightEvents;
    }

    public void setLowInFlightEvents(int lowInFlightEvents) {
        this.lowInFlightEvents = lowInFlightEvents;
    }

    public long getStableWindowMs() {
        return stableWindowMs;
    }

    public void setStableWindowMs(long stableWindowMs) {
        this.stableWindowMs = stableWindowMs;
    }
}
