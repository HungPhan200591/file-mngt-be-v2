package com.filemngt.v2.scan.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "scan.outbox")
@Validated
/** Cấu hình bounded relay; mọi deadline phải nằm trong lease của outbox record. */
public class OutboxDrainProperties {
    private boolean enabled = true;
    private boolean continuousDrainEnabled = true;

    @Min(1)
    private int maxInFlightEvents = 500;

    @Min(1)
    private int claimSize = 500;

    @Min(1)
    private long schedulerDelayMs = 1;

    @Min(1)
    private long drainTimeSliceMs = 100;

    @Min(1)
    private long idleDelayMs = 50;

    @Min(1)
    private long leaseSeconds = 30;

    @Min(1)
    private long producerDeliveryTimeoutMs = 3_000;

    @Min(0)
    private long acknowledgementSlackMs = 500;

    @Min(0)
    private long conditionalMarkBudgetMs = 500;

    @Min(1)
    private long safetyMarginMs = 500;

    @Min(1)
    private int completionFlushSize = 500;

    @Min(1)
    private long completionFlushIntervalMs = 50;

    @Min(1)
    private long shutdownGraceMs = 5_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isContinuousDrainEnabled() {
        return continuousDrainEnabled;
    }

    public void setContinuousDrainEnabled(boolean continuousDrainEnabled) {
        this.continuousDrainEnabled = continuousDrainEnabled;
    }

    public int getMaxInFlightEvents() {
        return maxInFlightEvents;
    }

    public void setMaxInFlightEvents(int maxInFlightEvents) {
        this.maxInFlightEvents = maxInFlightEvents;
    }

    public int getClaimSize() {
        return claimSize;
    }

    public void setClaimSize(int claimSize) {
        this.claimSize = claimSize;
    }

    public long getSchedulerDelayMs() {
        return schedulerDelayMs;
    }

    public void setSchedulerDelayMs(long schedulerDelayMs) {
        this.schedulerDelayMs = schedulerDelayMs;
    }

    public long getDrainTimeSliceMs() {
        return drainTimeSliceMs;
    }

    public void setDrainTimeSliceMs(long drainTimeSliceMs) {
        this.drainTimeSliceMs = drainTimeSliceMs;
    }

    public long getIdleDelayMs() {
        return idleDelayMs;
    }

    public void setIdleDelayMs(long idleDelayMs) {
        this.idleDelayMs = idleDelayMs;
    }

    public long getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(long leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public long getProducerDeliveryTimeoutMs() {
        return producerDeliveryTimeoutMs;
    }

    public void setProducerDeliveryTimeoutMs(long producerDeliveryTimeoutMs) {
        this.producerDeliveryTimeoutMs = producerDeliveryTimeoutMs;
    }

    public long getAcknowledgementSlackMs() {
        return acknowledgementSlackMs;
    }

    public void setAcknowledgementSlackMs(long acknowledgementSlackMs) {
        this.acknowledgementSlackMs = acknowledgementSlackMs;
    }

    public long getConditionalMarkBudgetMs() {
        return conditionalMarkBudgetMs;
    }

    public void setConditionalMarkBudgetMs(long conditionalMarkBudgetMs) {
        this.conditionalMarkBudgetMs = conditionalMarkBudgetMs;
    }

    public long getSafetyMarginMs() {
        return safetyMarginMs;
    }

    public void setSafetyMarginMs(long safetyMarginMs) {
        this.safetyMarginMs = safetyMarginMs;
    }

    public int getCompletionFlushSize() {
        return completionFlushSize;
    }

    public void setCompletionFlushSize(int completionFlushSize) {
        this.completionFlushSize = completionFlushSize;
    }

    public long getCompletionFlushIntervalMs() {
        return completionFlushIntervalMs;
    }

    public void setCompletionFlushIntervalMs(long completionFlushIntervalMs) {
        this.completionFlushIntervalMs = completionFlushIntervalMs;
    }

    public long getShutdownGraceMs() {
        return shutdownGraceMs;
    }

    public void setShutdownGraceMs(long shutdownGraceMs) {
        this.shutdownGraceMs = shutdownGraceMs;
    }

    public long acknowledgementDeadlineMs() {
        return producerDeliveryTimeoutMs + acknowledgementSlackMs;
    }
}
