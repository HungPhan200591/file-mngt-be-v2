package com.filemngt.v2.catalog.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Catalog sở hữu DLT topology; partition gốc phải ánh xạ được 1:1 sang partition DLT. */
@Configuration
@ConditionalOnProperty(name = "catalog.kafka.operation-consumer.enabled", havingValue = "true")
public class CatalogKafkaTopicConfiguration {
    @Bean
    @ConditionalOnProperty(
            name = "catalog.kafka.operation-consumer.topic-provisioning-enabled",
            havingValue = "true",
            matchIfMissing = true)
    NewTopic mediaFileDiscoveredDltTopic(
            @Value("${catalog.kafka.operation-consumer.topic-partitions:12}") int partitions) {
        if (partitions < 1) throw new IllegalArgumentException("Catalog operation topic partitions must be positive");
        return TopicBuilder.name("media.file.discovered.v2.DLT")
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
