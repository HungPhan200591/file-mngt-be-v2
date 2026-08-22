package com.filemngt.v2.catalog.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.catalog.application.CatalogDeadLetterService;
import com.filemngt.v2.catalog.application.CatalogOutboxMetrics;
import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class CatalogDeadLetterObserverTest {
    @Test
    void derivesShardRoutingFromCanonicalDiscoveryPayload() {
        CatalogDeadLetterService service = mock(CatalogDeadLetterService.class);
        CatalogOutboxMetrics metrics = mock(CatalogOutboxMetrics.class);
        when(service.record(any())).thenReturn(true);
        UUID operationId = UUID.randomUUID();
        var observer = new CatalogDeadLetterObserver(service, metrics, new ObjectMapper());
        var record =
                new ConsumerRecord<>("media.file.discovered.v2.DLT", 1, 42L, "dlt-key", """
                {"operationId":"%s","region":"JOKE","subjectType":"VIDEO","identityKey":"START-001"}
                """.formatted(operationId));

        observer.observe(record);

        var command = capturedCommand(service);
        assertThat(command.operationId()).isEqualTo(operationId);
        assertThat(command.routingBucket())
                .isEqualTo(ApprovalCompletionShardRouter.routingBucket("JOKE", "VIDEO", "START-001"));
        verify(metrics).deadLetterReceived();
    }

    @Test
    void preservesOperationIdAndUsesParentGateForUnroutableMarkerPayload() {
        CatalogDeadLetterService service = mock(CatalogDeadLetterService.class);
        CatalogOutboxMetrics metrics = mock(CatalogOutboxMetrics.class);
        when(service.record(any())).thenReturn(true);
        UUID operationId = UUID.randomUUID();
        var observer = new CatalogDeadLetterObserver(service, metrics, new ObjectMapper());
        var record = new ConsumerRecord<>(
                "media.approval.shard.completed.v1.DLT",
                2,
                7L,
                "marker-key",
                "{\"operationId\":\"%s\"}".formatted(operationId));

        observer.observe(record);

        var command = capturedCommand(service);
        assertThat(command.operationId()).isEqualTo(operationId);
        assertThat(command.routingBucket()).isNull();
        verify(metrics).deadLetterReceived();
    }

    private CatalogDeadLetterService.DeadLetterCommand capturedCommand(CatalogDeadLetterService service) {
        var captor = ArgumentCaptor.forClass(CatalogDeadLetterService.DeadLetterCommand.class);
        verify(service).record(captor.capture());
        return captor.getValue();
    }
}
