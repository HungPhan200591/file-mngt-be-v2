package com.filemngt.v2.query.adapter.in.event;

import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.query.application.QueryProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class MediaSubjectChangedConsumer {
    private final ObjectMapper json;
    private final QueryProjectionService service;

    public MediaSubjectChangedConsumer(ObjectMapper json, QueryProjectionService service) {
        this.json = json;
        this.service = service;
    }

    @KafkaListener(
            topics = "media.subject.changed.v1",
            groupId = "query-service",
            concurrency = "${query.kafka.consumer.concurrency:8}",
            autoStartup = "${query.kafka.consumer.enabled:true}")
    public void consume(ConsumerRecord<String, String> record) throws JacksonException {
        try (var ignored = KafkaTracingHeaderPropagation.extractAndSetMdc(record)) {
            service.handle(json.readValue(record.value(), MediaSubjectChangedV1.class));
        }
    }
}
