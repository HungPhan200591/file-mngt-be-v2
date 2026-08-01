package com.filemngt.v2.catalog.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class CatalogKafkaErrorHandlingConfig {

    private static final long RETRY_BACK_OFF_MILLIS = 1_000;
    private static final long RETRY_COUNT = 2;

    @Bean
    CommonErrorHandler catalogKafkaErrorHandler(KafkaTemplate<Object, Object> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafka, (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_BACK_OFF_MILLIS, RETRY_COUNT));
    }
}
