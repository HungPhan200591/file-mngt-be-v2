package com.filemngt.v2.scan.application.outbox;

@FunctionalInterface
/** Port publish payload outbox; application không phụ thuộc trực tiếp Kafka client. */
public interface OutboxMessagePublisher {
    /** Gửi payload theo topic và partition key đã được quyết định trong factory. */
    void publish(String topic, String key, String payload);

    default java.util.concurrent.CompletionStage<Void> publishAsync(String topic, String key, String payload) {
        publish(topic, key, payload);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
}
