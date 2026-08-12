package com.filemngt.v2.catalog.adapter.out.messaging;

import com.filemngt.v2.catalog.application.CatalogOutboxMessagePublisher;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaCatalogOutboxMessagePublisher implements CatalogOutboxMessagePublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafka;

    public KafkaCatalogOutboxMessagePublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            publishAsync(topic, key, payload).toCompletableFuture().get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing Catalog outbox event", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish Catalog outbox event", exception);
        }
    }

    @Override
    public java.util.concurrent.CompletionStage<Void> publishAsync(String topic, String key, String payload) {
        var record = new ProducerRecord<String, String>(topic, key, payload);
        KafkaTracingHeaderPropagation.injectTracingHeaders(record);
        return kafka.send(record).thenApply(result -> (Void) null).orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
