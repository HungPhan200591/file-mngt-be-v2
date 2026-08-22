package com.filemngt.v2.catalog.config;

import com.filemngt.v2.catalog.application.operation.CatalogOperationReliabilityMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

/** Tách retry thật khỏi poison record và DLT publication failure bằng low-cardinality counters. */
@Component
public class CatalogKafkaRetryMetricsListener implements RetryListener {
    private final CatalogOperationReliabilityMetrics metrics;

    public CatalogKafkaRetryMetricsListener(CatalogOperationReliabilityMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
        recordRetry(deliveryAttempt);
    }

    @Override
    public void failedDelivery(ConsumerRecords<?, ?> records, Exception exception, int deliveryAttempt) {
        recordRetry(deliveryAttempt);
    }

    @Override
    public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
        metrics.recordDltPublishFailure();
    }

    @Override
    public void recoveryFailed(ConsumerRecords<?, ?> records, Exception original, Exception failure) {
        metrics.recordDltPublishFailure();
    }

    private void recordRetry(int deliveryAttempt) {
        if (deliveryAttempt > 1) metrics.recordRetry("kafka");
    }
}
