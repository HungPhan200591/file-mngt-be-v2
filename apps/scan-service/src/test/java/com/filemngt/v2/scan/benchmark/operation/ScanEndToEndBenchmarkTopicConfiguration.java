package com.filemngt.v2.scan.benchmark.operation;

import static com.filemngt.v2.scan.benchmark.fixture.ScanEndToEndBenchmarkSettings.DISCOVERY_TOPIC_PARTITIONS;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@TestConfiguration
class ScanEndToEndBenchmarkTopicConfiguration {
    @Bean
    NewTopic mediaFileDiscoveredTopic() {
        return topic("media.file.discovered.v2", DISCOVERY_TOPIC_PARTITIONS);
    }

    @Bean
    NewTopic mediaApprovalShardCompletedTopic() {
        return topic("media.approval.shard.completed.v1", DISCOVERY_TOPIC_PARTITIONS);
    }

    @Bean
    NewTopic mediaApprovalWatermarkTopic() {
        return topic("media.approval.watermark.v1", 1);
    }

    private NewTopic topic(String name, int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
