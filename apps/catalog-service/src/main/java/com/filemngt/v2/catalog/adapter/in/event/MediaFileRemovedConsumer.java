package com.filemngt.v2.catalog.adapter.in.event;

import com.filemngt.v2.catalog.application.CatalogFileRemovalService;
import com.filemngt.v2.contracts.events.MediaFileRemovedV1;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class MediaFileRemovedConsumer {
    private final ObjectMapper json;
    private final CatalogFileRemovalService service;

    public MediaFileRemovedConsumer(ObjectMapper json, CatalogFileRemovalService service) {
        this.json = json;
        this.service = service;
    }

    @KafkaListener(
            topics = "media.file.removed.v1",
            groupId = "catalog-service",
            autoStartup = "${catalog.kafka.consumer.enabled:true}")
    public void consume(ConsumerRecord<String, String> record) throws JacksonException {
        try (var ignored = KafkaTracingHeaderPropagation.extractAndSetMdc(record)) {
            service.handle(json.readValue(record.value(), MediaFileRemovedV1.class));
        }
    }
}
