package com.filemngt.v2.catalog.adapter.in.event;

import com.filemngt.v2.catalog.application.CatalogFileDiscoveryService;
import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Bounded operation ingest; canonical reducer chạy sau equality gate, không hydrate JPA aggregate ở đây. */
@Component
@ConditionalOnProperty(name = "catalog.kafka.operation-consumer.enabled", havingValue = "true")
public class CatalogOperationBatchConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationBatchConsumer.class);
    private final ObjectMapper json;
    private final CatalogOperationStageStore stage;
    private final CatalogFileDiscoveryService legacyDiscovery;
    private final int sliceRecordLimit;
    private final int sliceByteLimit;

    public CatalogOperationBatchConsumer(
            ObjectMapper json,
            CatalogOperationStageStore stage,
            CatalogFileDiscoveryService legacyDiscovery,
            @Value("${catalog.kafka.operation-consumer.slice-records:2000}") int sliceRecordLimit,
            @Value("${catalog.kafka.operation-consumer.slice-bytes:16777216}") int sliceByteLimit) {
        this.json = json;
        this.stage = stage;
        this.legacyDiscovery = legacyDiscovery;
        if (sliceRecordLimit < 1 || sliceByteLimit < 1) {
            throw new IllegalArgumentException("Catalog operation slice limits must be positive");
        }
        this.sliceRecordLimit = sliceRecordLimit;
        this.sliceByteLimit = sliceByteLimit;
    }

    @KafkaListener(
            topics = "media.file.discovered.v2",
            groupId = "catalog-operation-coalescing",
            containerFactory = "catalogOperationBatchFactory",
            autoStartup = "${catalog.kafka.operation-consumer.enabled:false}")
    public void consume(List<ConsumerRecord<String, String>> records) {
        if (records == null || records.isEmpty()) return;
        var batch = new BatchAccumulator(records.size());
        for (int index = 0; index < records.size(); index++) {
            processRecord(records.get(index), index, batch);
        }
        batch.flush();
        LOGGER.debug("Catalog operation ingest received={} inserted={}", records.size(), batch.inserted);
    }

    private void processRecord(ConsumerRecord<String, String> record, int index, BatchAccumulator batch) {
        MediaFileDiscoveredV2 event;
        CatalogOperationStageStore.RecordCoordinate coordinate;
        try (var ignored = KafkaTracingHeaderPropagation.extractAndSetMdc(record)) {
            event = parse(record);
            validateEvent(event);
            if (event.operationId() != null) validateOperationMetadata(event);
            var trace = KafkaTracingHeaderPropagation.captureOutboxTraceContext();
            coordinate = new CatalogOperationStageStore.RecordCoordinate(
                    record.partition(), record.offset(), trace.correlationId(), trace.traceparent());
        } catch (RuntimeException exception) {
            batch.flush();
            LOGGER.debug("Catalog operation durable prefix inserted={} failedIndex={}", batch.inserted, index);
            throw new BatchListenerFailedException("Invalid catalog operation record", exception, record);
        }
        if (event.operationId() == null) {
            batch.flush();
            legacyDiscovery.handleV2(event);
            return;
        }
        batch.add(event, coordinate, payloadBytes(record));
    }

    private void validateEvent(MediaFileDiscoveredV2 event) {
        if (event.eventId() == null
                || event.timestamp() == null
                || event.region() == null
                || event.subjectType() == null
                || event.identityKey() == null) {
            throw new IllegalArgumentException("operation discovery required fields are missing");
        }
        Region.valueOf(event.region());
        SubjectType.valueOf(event.subjectType());
        if (event.role() != null) MediaAssetRole.valueOf(event.role());
    }

    private void validateOperationMetadata(MediaFileDiscoveredV2 event) {
        if (event.batchId() == null || event.batchId().isBlank() || event.scanRunId() == null) {
            throw new IllegalArgumentException("operation discovery metadata is missing");
        }
    }

    private int payloadBytes(ConsumerRecord<String, String> record) {
        int serialized = record.serializedValueSize();
        return serialized >= 0 ? serialized : record.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private MediaFileDiscoveredV2 parse(ConsumerRecord<String, String> record) {
        try {
            var eventType = json.readTree(record.value()).path("eventType").asText(null);
            if (!"media.file.discovered.v2".equals(eventType)) {
                throw new IllegalArgumentException("Unsupported media discovery eventType: " + eventType);
            }
            return json.readValue(record.value(), MediaFileDiscoveredV2.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not parse catalog operation record", exception);
        }
    }

    private final class BatchAccumulator {
        private final List<MediaFileDiscoveredV2> events;
        private final List<CatalogOperationStageStore.RecordCoordinate> coordinates;
        private int bytes;
        private int inserted;

        private BatchAccumulator(int pollSize) {
            int capacity = Math.min(pollSize, sliceRecordLimit);
            events = new ArrayList<>(capacity);
            coordinates = new ArrayList<>(capacity);
        }

        private void add(
                MediaFileDiscoveredV2 event, CatalogOperationStageStore.RecordCoordinate coordinate, int payloadBytes) {
            events.add(event);
            coordinates.add(coordinate);
            bytes += payloadBytes;
            if (events.size() >= sliceRecordLimit || bytes >= sliceByteLimit) flush();
        }

        private void flush() {
            if (events.isEmpty()) return;
            inserted += stage.ingest(List.copyOf(events), List.copyOf(coordinates));
            events.clear();
            coordinates.clear();
            bytes = 0;
        }
    }
}
