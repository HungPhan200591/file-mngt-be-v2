package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.scan.config.OutboxDrainProperties;
import com.filemngt.v2.scan.config.OutboxPressureProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
/** Fail fast khi acknowledgement và conditional mark có thể hết lease trước khi hoàn thành. */
public class OutboxLeaseBudgetPolicy {
    private final OutboxDrainProperties properties;
    private final OutboxPressureProperties pressure;

    public OutboxLeaseBudgetPolicy(OutboxDrainProperties properties, OutboxPressureProperties pressure) {
        this.properties = properties;
        this.pressure = pressure;
    }

    @PostConstruct
    void validateAtStartup() {
        if (properties.isContinuousDrainEnabled() || properties.isLaneRelayEnabled()) {
            validate();
        }
    }

    public void validate() {
        long requiredMillis = properties.getProducerDeliveryTimeoutMs()
                + properties.getAcknowledgementSlackMs()
                + properties.getConditionalMarkBudgetMs()
                + properties.getSafetyMarginMs();
        long leaseMillis = properties.getLeaseSeconds() * 1_000;
        if (requiredMillis >= leaseMillis) {
            throw new IllegalStateException("scan.outbox lease budget phải nhỏ hơn lease duration");
        }
        if (pressure.getLowPendingAgeMs() > pressure.getHighPendingAgeMs()
                || pressure.getLowInFlightEvents() > pressure.getHighInFlightEvents()
                || pressure.getHighInFlightEvents() > properties.getMaxInFlightEvents()) {
            throw new IllegalStateException("scan.outbox pressure watermark không hợp lệ");
        }
        if (properties.isLaneRelayEnabled() && properties.getLaneWorkerConcurrency() > properties.getLaneCount()) {
            throw new IllegalStateException("scan.outbox lane worker concurrency vượt lane count cố định");
        }
    }
}
