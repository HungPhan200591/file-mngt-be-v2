package com.filemngt.v2.scan.config;

import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
/** Kafka producer dùng chung toàn service; virtual relay task không được tạo producer theo từng thread. */
public class ScanKafkaProducerConfiguration {

    @Bean
    DefaultKafkaProducerFactoryCustomizer scanKafkaProducerFactoryCustomizer() {
        return factory -> factory.setProducerPerThread(false);
    }
}
