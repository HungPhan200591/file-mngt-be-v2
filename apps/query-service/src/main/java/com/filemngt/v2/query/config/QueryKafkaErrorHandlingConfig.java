package com.filemngt.v2.query.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class QueryKafkaErrorHandlingConfig {
    @Bean
    CommonErrorHandler queryKafkaErrorHandler(KafkaTemplate<Object, Object> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafka, (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 2));
    }
}
