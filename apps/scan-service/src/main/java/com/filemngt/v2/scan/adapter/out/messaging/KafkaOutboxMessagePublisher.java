package com.filemngt.v2.scan.adapter.out.messaging;

import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.application.outbox.OutboxMessagePublisher;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
/** Adapter Kafka publish payload outbox tới topic đã được factory gắn vào event type. */
public class KafkaOutboxMessagePublisher implements OutboxMessagePublisher {
    private final KafkaTemplate<String, String> kafka;
    private final OutboxDrainProperties properties;

    public KafkaOutboxMessagePublisher(KafkaTemplate<String, String> kafka, OutboxDrainProperties properties) {
        this.kafka = kafka;
        this.properties = properties;
    }

    @Override
    /** Gửi payload đã serialize với partition key ổn định để broker giữ thứ tự theo media identity. */
    public void publish(String topic, String key, String payload) {
        publishAsync(topic, key, payload).toCompletableFuture().join();
    }

    @Override
    public java.util.concurrent.CompletionStage<Void> publishAsync(String topic, String key, String payload) {
        var record = new ProducerRecord<String, String>(topic, key, payload);
        KafkaTracingHeaderPropagation.injectTracingHeaders(record);
        return kafka.send(record)
                .thenApply(result -> (Void) null)
                .orTimeout(properties.acknowledgementDeadlineMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
