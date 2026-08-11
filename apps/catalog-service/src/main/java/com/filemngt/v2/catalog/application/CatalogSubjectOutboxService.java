package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectEntity;
import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CatalogSubjectOutboxService {

    private static final String EVENT_TYPE = "media.subject.changed.v1";

    private final CatalogOutboxEventRepository events;
    private final ObjectMapper json;

    public CatalogSubjectOutboxService(CatalogOutboxEventRepository events, ObjectMapper json) {
        this.events = events;
        this.json = json;
    }

    public void enqueue(MediaSubjectEntity subject) {
        var traceContext = KafkaTracingHeaderPropagation.captureOutboxTraceContext();
        var event = new MediaSubjectChangedV1(
                UUID.randomUUID(),
                EVENT_TYPE,
                Instant.now(),
                subject.id(),
                subject.version(),
                subject.region().name(),
                subject.subjectType().name(),
                subject.identityKey(),
                subject.displayTitle(),
                subject.baseCode(),
                subject.part(),
                subject.studioCode(),
                subject.actressNames().stream().sorted().toList(),
                subject.tagNames().stream().sorted().toList(),
                subject.createdAt(),
                subject.assets().stream()
                        .map(asset -> new MediaSubjectChangedV1.AssetSnapshot(
                                asset.id(), asset.role().name(), asset.relativePath(), asset.storageKey()))
                        .toList());
        events.save(new CatalogOutboxEventEntity(
                event.eventId(),
                subject.id(),
                subject.version(),
                event.eventType(),
                subject.id().toString(),
                serialize(event),
                traceContext.correlationId(),
                traceContext.traceparent(),
                event.occurredAt()));
    }

    private String serialize(MediaSubjectChangedV1 event) {
        try {
            return json.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new EventSerializationException(exception);
        }
    }

    public static class EventSerializationException extends RuntimeException {
        EventSerializationException(JacksonException cause) {
            super("Could not serialize media subject event", cause);
        }
    }
}
