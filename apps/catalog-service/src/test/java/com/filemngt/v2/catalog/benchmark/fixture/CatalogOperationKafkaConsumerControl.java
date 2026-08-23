package com.filemngt.v2.catalog.benchmark.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

/** Xác nhận consumer assignment ổn định trước khi combined benchmark bắt đầu publish workload. */
public final class CatalogOperationKafkaConsumerControl {
    private CatalogOperationKafkaConsumerControl() {}

    public static void awaitAssignments(
            KafkaListenerEndpointRegistry registry,
            String operationGroup,
            String discoveryTopic,
            int discoveryPartitions,
            int operationConsumerCount,
            String watermarkGroup,
            String watermarkTopic,
            String completionGroup,
            String completionTopic,
            int completionPartitions,
            Duration timeout) {
        await().alias("Catalog input consumers assigned")
                .pollInterval(Duration.ofMillis(50))
                .during(Duration.ofSeconds(1))
                .atMost(timeout)
                .untilAsserted(() -> {
                    assertThat(assignedPartitions(registry, operationGroup, discoveryTopic))
                            .isEqualTo(discoveryPartitions);
                    assertThat(assignedContainerCount(registry, operationGroup, discoveryTopic))
                            .isEqualTo(Math.min(discoveryPartitions, operationConsumerCount));
                    assertThat(assignedPartitions(registry, watermarkGroup, watermarkTopic))
                            .isEqualTo(1);
                    assertThat(assignedPartitions(registry, completionGroup, completionTopic))
                            .isEqualTo(completionPartitions);
                });
    }

    public static void pauseGroup(KafkaListenerEndpointRegistry registry, String groupId, Duration timeout) {
        var containers = containersForGroup(registry, groupId);
        assertThat(containers).isNotEmpty();
        containers.forEach(MessageListenerContainer::pause);
        await().alias("Catalog benchmark consumer group paused without rebalance")
                .pollInterval(Duration.ofMillis(20))
                .atMost(timeout)
                .untilAsserted(() -> assertThat(containers).allMatch(MessageListenerContainer::isContainerPaused));
    }

    public static void resumeGroup(KafkaListenerEndpointRegistry registry, String groupId) {
        containersForGroup(registry, groupId).forEach(MessageListenerContainer::resume);
    }

    private static java.util.List<MessageListenerContainer> containersForGroup(
            KafkaListenerEndpointRegistry registry, String groupId) {
        var containers = new ArrayList<MessageListenerContainer>();
        for (var container : registry.getListenerContainers()) {
            if (groupId.equals(container.getGroupId())) {
                containers.add(container);
            }
        }
        return containers;
    }

    private static int assignedPartitions(KafkaListenerEndpointRegistry registry, String groupId, String topic) {
        int assigned = 0;
        for (var container : registry.getListenerContainers()) {
            if (!groupId.equals(container.getGroupId()) || container.getAssignedPartitions() == null) {
                continue;
            }
            for (var partition : container.getAssignedPartitions()) {
                if (topic.equals(partition.topic())) {
                    assigned++;
                }
            }
        }
        return assigned;
    }

    private static int assignedContainerCount(KafkaListenerEndpointRegistry registry, String groupId, String topic) {
        int assigned = 0;
        for (var container : registry.getListenerContainers()) {
            if (!groupId.equals(container.getGroupId())) {
                continue;
            }
            assigned += Math.toIntExact(container.getAssignmentsByClientId().values().stream()
                    .filter(partitions -> partitions.stream().anyMatch(partition -> topic.equals(partition.topic())))
                    .count());
        }
        return assigned;
    }
}
