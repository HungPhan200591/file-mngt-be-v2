package com.filemngt.v2.scan.application;

@FunctionalInterface
public interface OutboxMessagePublisher {
    void publish(String topic, String key, String payload);
}
