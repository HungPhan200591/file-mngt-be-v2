package com.filemngt.v2.catalog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

@Configuration
@ConditionalOnProperty(name = "catalog.kafka.operation-consumer.enabled", havingValue = "true")
public class CatalogOperationKafkaConfig {
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> catalogOperationBatchFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler catalogKafkaErrorHandler,
            @Value("${catalog.kafka.operation-consumer.concurrency:1}") int concurrency) {
        if (concurrency < 1) throw new IllegalArgumentException("operation consumer concurrency must be positive");
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(catalogKafkaErrorHandler);
        factory.getContainerProperties().setAckMode(AckMode.BATCH);
        factory.getContainerProperties().setMicrometerEnabled(false);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
