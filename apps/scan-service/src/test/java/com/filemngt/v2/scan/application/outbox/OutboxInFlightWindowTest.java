package com.filemngt.v2.scan.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.config.OutboxDrainProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxInFlightWindowTest {

    @Test
    void keepsCompletionWithinReservedWindowUntilCoordinatorPersistsIt() {
        OutboxInFlightWindow window = window(2);
        window.reserve(2);
        window.complete(new OutboxCompletion(UUID.randomUUID(), Instant.now(), null));

        assertThat(window.freeSlots()).isZero();
        assertThat(window.completionDepth()).isOne();

        assertThat(window.drain(2)).hasSize(1);
        window.release(1);

        assertThat(window.freeSlots()).isEqualTo(1);
    }

    private OutboxInFlightWindow window(int maximum) {
        OutboxDrainProperties properties = new OutboxDrainProperties();
        properties.setMaxInFlightEvents(maximum);
        return new OutboxInFlightWindow(properties);
    }
}
