package com.filemngt.v2.scan.application.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemngt.v2.scan.config.OutboxDrainProperties;
import com.filemngt.v2.scan.config.OutboxPressureProperties;
import org.junit.jupiter.api.Test;

class OutboxLeaseBudgetPolicyTest {

    @Test
    void rejectsDeadlineBudgetThatWouldExhaustLease() {
        OutboxDrainProperties properties = new OutboxDrainProperties();
        properties.setLeaseSeconds(3);
        properties.setProducerDeliveryTimeoutMs(2_000);
        properties.setAcknowledgementSlackMs(500);
        properties.setConditionalMarkBudgetMs(500);
        properties.setSafetyMarginMs(500);

        assertThatThrownBy(() -> new OutboxLeaseBudgetPolicy(properties, new OutboxPressureProperties()).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease budget");
    }
}
