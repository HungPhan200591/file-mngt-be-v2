package com.filemngt.v2.catalog.adapter.in.event;

import com.filemngt.v2.catalog.application.operation.CatalogCompletionShardStore;
import com.filemngt.v2.contracts.events.MediaApprovalShardCompletedV1;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Consumer idempotent cho marker completion shard; arrival order với discovery topic không là invariant. */
@Component
@ConditionalOnProperty(name = "catalog.kafka.operation-consumer.enabled", havingValue = "true")
public class CatalogApprovalShardCompletedConsumer {
    private final ObjectMapper json;
    private final CatalogCompletionShardStore shards;

    public CatalogApprovalShardCompletedConsumer(ObjectMapper json, CatalogCompletionShardStore shards) {
        this.json = json;
        this.shards = shards;
    }

    @KafkaListener(
            topics = "media.approval.shard.completed.v1",
            groupId = "catalog-operation-shard-completion",
            autoStartup = "${catalog.kafka.operation-consumer.enabled:false}")
    public void consume(ConsumerRecord<String, String> record) {
        try (var ignored = KafkaTracingHeaderPropagation.extractAndSetMdc(record)) {
            var marker = json.readValue(record.value(), MediaApprovalShardCompletedV1.class);
            var trace = KafkaTracingHeaderPropagation.captureOutboxTraceContext();
            shards.accept(marker, trace.correlationId(), trace.traceparent());
        } catch (CatalogInputContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CatalogInputContractException("Could not process approval shard completion marker", exception);
        }
    }
}
