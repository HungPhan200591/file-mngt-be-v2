package com.filemngt.v2.catalog.adapter.in.event;

import com.filemngt.v2.catalog.application.CatalogDeadLetterService;
import com.filemngt.v2.catalog.application.CatalogOutboxMetrics;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.kafka.dlt-observer.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogDeadLetterObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogDeadLetterObserver.class);

    private final CatalogDeadLetterService service;
    private final CatalogOutboxMetrics metrics;

    public CatalogDeadLetterObserver(CatalogDeadLetterService service, CatalogOutboxMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "media.file.discovered.v2.DLT",
            groupId = "catalog-dlt-observer",
            autoStartup = "${catalog.kafka.dlt-observer.enabled:true}")
    public void observe(ConsumerRecord<String, String> record) {
        try (var ignored = com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation.extractAndSetMdc(record)) {
            boolean recorded = service.record(new CatalogDeadLetterService.DeadLetterCommand(
                    headerString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, originalTopic(record.topic())),
                    headerInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION, record.partition()),
                    headerLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET, record.offset()),
                    record.key(),
                    record.value(),
                    headerString(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, null)));
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
}
