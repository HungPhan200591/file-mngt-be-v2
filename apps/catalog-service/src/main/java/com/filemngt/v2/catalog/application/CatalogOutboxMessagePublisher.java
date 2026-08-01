package com.filemngt.v2.catalog.application;

@FunctionalInterface
public interface CatalogOutboxMessagePublisher {
    void publish(String topic, String key, String payload) throws Exception;
}
