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
    private static final String COMPLETION_TOPIC = "media.approval.shard.completed.v1";

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

    @Bean
    @ConditionalOnProperty(
            name = "catalog.kafka.operation-consumer.topic-provisioning-enabled",
            havingValue = "true",
            matchIfMissing = true)
    NewTopic mediaApprovalShardCompletedTopic(
            @Value("${catalog.kafka.operation-consumer.completion-topic-partitions:12}") int partitions) {
        return completionTopic(COMPLETION_TOPIC, partitions);
    }

    @Bean
    @ConditionalOnProperty(
            name = "catalog.kafka.operation-consumer.topic-provisioning-enabled",
            havingValue = "true",
            matchIfMissing = true)
    NewTopic mediaApprovalShardCompletedDltTopic(
            @Value("${catalog.kafka.operation-consumer.completion-topic-partitions:12}") int partitions) {
        return completionTopic(COMPLETION_TOPIC + ".DLT", partitions);
    }

    private NewTopic completionTopic(String name, int partitions) {
        if (partitions < 1) {
            throw new IllegalArgumentException("Catalog completion topic partitions must be positive");
        }
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
