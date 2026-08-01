package com.filemngt.v2.scan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanOutboxPublisherTest {

    @Test
    void marksEventPublishedAfterBrokerAcknowledgement() {
        ScanOutboxEventRepository events = mock(ScanOutboxEventRepository.class);
        OutboxMessagePublisher messages = mock(OutboxMessagePublisher.class);
        ScanOutboxEventEntity event = event();
        when(events.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));

        new ScanOutboxPublisher(events, messages).publishPending();

        assertThat(event.publishedAt()).isNotNull();
        assertThat(event.attemptCount()).isZero();
        assertThat(event.lastError()).isNull();
        verify(messages).publish(event.eventType(), event.partitionKey(), event.payload());
        verify(events).save(event);
    }

    @Test
    void keepsEventPendingAndRecordsFailureForNextPoll() {
        ScanOutboxEventRepository events = mock(ScanOutboxEventRepository.class);
        OutboxMessagePublisher messages = mock(OutboxMessagePublisher.class);
        ScanOutboxEventEntity event = event();
        when(events.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(messages)
                .publish(event.eventType(), event.partitionKey(), event.payload());

        new ScanOutboxPublisher(events, messages).publishPending();

        assertThat(event.publishedAt()).isNull();
        assertThat(event.attemptCount()).isOne();
        assertThat(event.lastError()).contains("broker unavailable");
        verify(events).save(event);
    }

    private ScanOutboxEventEntity event() {
        return new ScanOutboxEventEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "media.file.discovered.v1",
                "JOKE:VIDEO:JOKE-001",
                "{}",
                Instant.now());
    }
}
