package com.filemngt.v2.catalog.adapter.in.event;

import com.filemngt.v2.catalog.application.CatalogDeadLetterService;
import com.filemngt.v2.catalog.application.CatalogOutboxMetrics;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "catalog.kafka.dlt-observer.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogDeadLetterObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogDeadLetterObserver.class);

    private final CatalogDeadLetterService service;
    private final CatalogOutboxMetrics metrics;
    private final ObjectMapper json;

    public CatalogDeadLetterObserver(
            CatalogDeadLetterService service, CatalogOutboxMetrics metrics, ObjectMapper json) {
        this.service = service;
        this.metrics = metrics;
        this.json = json;
    }

    @KafkaListener(
            topics = {"media.file.discovered.v2.DLT", "media.approval.shard.completed.v1.DLT"},
            groupId = "catalog-dlt-observer",
            autoStartup = "${catalog.kafka.dlt-observer.enabled:true}")
    public void observe(ConsumerRecord<String, String> record) {
        try (var ignored = com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation.extractAndSetMdc(record)) {
            PayloadContext payload = payloadContext(record.value());
            boolean recorded = service.record(new CatalogDeadLetterService.DeadLetterCommand(
                    headerString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, originalTopic(record.topic())),
                    headerInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION, record.partition()),
                    headerLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET, record.offset()),
                    record.key(),
                    record.value(),
                    headerString(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, null),
                    payload.operationId(),
                    payload.routingBucket(),
                    "CATALOG_INPUT_DLT"));
            if (recorded) {
                metrics.deadLetterReceived();
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not observe Catalog dead letter topic={} partition={} offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception);
            throw exception;
        }
    }

    private PayloadContext payloadContext(String payload) {
        try {
            var document = json.readTree(payload);
            String operationId = document.path("operationId").asText(null);
            String region = document.path("region").asText(null);
            String subjectType = document.path("subjectType").asText(null);
            String identityKey = document.path("identityKey").asText(null);
            return new PayloadContext(
                    operationId == null ? null : UUID.fromString(operationId),
                    routingBucket(region, subjectType, identityKey));
        } catch (Exception exception) {
            return new PayloadContext(null, null);
        }
    }

    private Integer routingBucket(String region, String subjectType, String identityKey) {
        if (region == null || subjectType == null || identityKey == null || identityKey.isBlank()) {
            return null;
        }
        try {
            Region.valueOf(region);
            SubjectType.valueOf(subjectType);
            return ApprovalCompletionShardRouter.routingBucket(region, subjectType, identityKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String originalTopic(String topic) {
        return topic.endsWith(".DLT") ? topic.substring(0, topic.length() - 4) : topic;
    }

    private String headerString(ConsumerRecord<String, String> record, String name, String fallback) {
        Header header = record.headers().lastHeader(name);
        return header == null ? fallback : new String(header.value(), StandardCharsets.UTF_8);
    }

    private int headerInt(ConsumerRecord<String, String> record, String name, int fallback) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value().length != Integer.BYTES
                ? fallback
                : ByteBuffer.wrap(header.value()).getInt();
    }

    private long headerLong(ConsumerRecord<String, String> record, String name, long fallback) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value().length != Long.BYTES
                ? fallback
                : ByteBuffer.wrap(header.value()).getLong();
    }

    private record PayloadContext(UUID operationId, Integer routingBucket) {}
}
