package com.filemngt.v2.catalog.benchmark.operation;

import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.DISCOVERY_PARTITIONS;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@TestConfiguration
class CatalogOperationEndToEndBenchmarkTopicConfiguration {
    @Bean
    NewTopic mediaFileDiscoveredTopic() {
        return topic("media.file.discovered.v2", DISCOVERY_PARTITIONS);
    }

    @Bean
    NewTopic mediaFileDiscoveredDltTopic() {
        return topic("media.file.discovered.v2.DLT", DISCOVERY_PARTITIONS);
    }

    @Bean
    NewTopic mediaApprovalWatermarkTopic() {
        return topic("media.approval.watermark.v1", 1);
    }

    @Bean
    NewTopic mediaApprovalShardCompletedTopic() {
        return topic("media.approval.shard.completed.v1", DISCOVERY_PARTITIONS);
    }

    @Bean
    NewTopic mediaApprovalShardCompletedDltTopic() {
        return topic("media.approval.shard.completed.v1.DLT", DISCOVERY_PARTITIONS);
    }

    @Bean
    NewTopic mediaSubjectChangedTopic() {
        return topic("media.subject.changed.v2", DISCOVERY_PARTITIONS);
    }

    private NewTopic topic(String name, int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
