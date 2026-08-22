package com.filemngt.v2.catalog.benchmark.operation;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@TestConfiguration
class CatalogOperationEndToEndBenchmarkTopicConfiguration {
    @Bean
    NewTopic mediaFileDiscoveredTopic() {
        return topic("media.file.discovered.v2", 4);
    }

    @Bean
    NewTopic mediaApprovalWatermarkTopic() {
        return topic("media.approval.watermark.v1", 1);
    }

    @Bean
    NewTopic mediaSubjectChangedTopic() {
        return topic("media.subject.changed.v2", 4);
    }

    private NewTopic topic(String name, int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
