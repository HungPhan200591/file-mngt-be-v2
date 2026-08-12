package com.filemngt.v2.catalog.application;

@FunctionalInterface
public interface CatalogOutboxMessagePublisher {
    void publish(String topic, String key, String payload) throws Exception;

    default java.util.concurrent.CompletionStage<Void> publishAsync(String topic, String key, String payload)
            throws Exception {
        publish(topic, key, payload);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
}
