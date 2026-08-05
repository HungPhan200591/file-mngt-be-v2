package com.filemngt.v2.scan.adapter.out.messaging;

import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.application.OutboxMessagePublisher;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
/** Adapter Kafka publish payload outbox tới topic đã được factory gắn vào event type. */
public class KafkaOutboxMessagePublisher implements OutboxMessagePublisher {
    private final KafkaTemplate<String, String> kafka;

    public KafkaOutboxMessagePublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    /** Gửi payload đã serialize với partition key ổn định để broker giữ thứ tự theo media identity. */
    public void publish(String topic, String key, String payload) {
        var record = new ProducerRecord<String, String>(topic, key, payload);
        KafkaTracingHeaderPropagation.injectTracingHeaders(record);
        kafka.send(record).join();
    }
}
