package com.filemngt.v2.catalog.adapter.in.event;

import com.filemngt.v2.catalog.application.CatalogFileDiscoveryService;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV1;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class MediaFileDiscoveredConsumer {
    private final ObjectMapper json;
    private final CatalogFileDiscoveryService service;

    public MediaFileDiscoveredConsumer(ObjectMapper json, CatalogFileDiscoveryService service) {
        this.json = json;
        this.service = service;
    }

    @KafkaListener(
            topics = "media.file.discovered.v1",
            groupId = "catalog-service",
            autoStartup = "${catalog.kafka.consumer.enabled:true}")
    public void consume(String payload) throws JacksonException {
        service.handle(json.readValue(payload, MediaFileDiscoveredV1.class));
    }
}
