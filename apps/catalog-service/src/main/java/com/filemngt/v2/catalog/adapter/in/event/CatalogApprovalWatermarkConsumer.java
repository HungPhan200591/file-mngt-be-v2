package com.filemngt.v2.catalog.adapter.in.event;

import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.contracts.events.MediaApprovalWatermarkV1;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "catalog.kafka.operation-consumer.enabled", havingValue = "true")
public class CatalogApprovalWatermarkConsumer {
    private final ObjectMapper json;
    private final CatalogOperationStageStore stage;

    public CatalogApprovalWatermarkConsumer(ObjectMapper json, CatalogOperationStageStore stage) {
        this.json = json;
        this.stage = stage;
    }

    @KafkaListener(
            topics = "media.approval.watermark.v1",
            groupId = "catalog-operation-watermark",
            autoStartup = "${catalog.kafka.operation-consumer.enabled:false}")
    public void consume(ConsumerRecord<String, String> record) {
        try (var ignored = KafkaTracingHeaderPropagation.extractAndSetMdc(record)) {
            var watermark = parse(record);
            if (watermark.stageSequence() != 10 || !"APPROVAL_COMMITTED".equals(watermark.stage())) return;
            var trace = KafkaTracingHeaderPropagation.captureOutboxTraceContext();
            stage.acceptWatermark(watermark, trace.correlationId(), trace.traceparent());
        }
    }

    private MediaApprovalWatermarkV1 parse(ConsumerRecord<String, String> record) {
        try {
            return json.readValue(record.value(), MediaApprovalWatermarkV1.class);
        } catch (Exception exception) {
            throw new CatalogInputContractException("Could not parse approval watermark", exception);
        }
    }
}
