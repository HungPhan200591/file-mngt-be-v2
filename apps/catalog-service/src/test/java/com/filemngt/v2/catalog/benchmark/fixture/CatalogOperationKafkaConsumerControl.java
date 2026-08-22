package com.filemngt.v2.catalog.benchmark.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;

/** Điều khiển cooperative pause/resume để seed Kafka nằm ngoài benchmark clock mà không rebalance consumer group. */
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

    public static void pause(KafkaListenerEndpointRegistry registry, List<String> groupIds, Duration timeout) {
        for (var container : containers(registry, groupIds)) {
            container.pause();
        }
        await().alias("Catalog input consumers paused without leaving their groups")
                .pollInterval(Duration.ofMillis(20))
                .atMost(timeout)
                .untilAsserted(() -> assertThat(listenerContainers(registry, groupIds))
                        .isNotEmpty()
                        .allMatch(MessageListenerContainer::isContainerPaused));
    }

    public static void resume(KafkaListenerEndpointRegistry registry, List<String> groupIds) {
        for (var container : containers(registry, groupIds)) {
            container.resume();
        }
    }

    public static void awaitResumed(KafkaListenerEndpointRegistry registry, List<String> groupIds, Duration timeout) {
        await().alias("Catalog input consumers resumed after a completed poll")
                .pollInterval(Duration.ofMillis(20))
                .atMost(timeout)
                .untilAsserted(() -> assertThat(listenerContainers(registry, groupIds))
                        .isNotEmpty()
                        .allMatch(container -> !container.isPauseRequested() && !container.isContainerPaused()));
    }

    private static List<MessageListenerContainer> containers(
            KafkaListenerEndpointRegistry registry, List<String> groupIds) {
        var containers = new ArrayList<MessageListenerContainer>();
        for (var container : registry.getListenerContainers()) {
            if (groupIds.contains(container.getGroupId())) {
                containers.add(container);
            }
        }
        return containers;
    }

    private static List<MessageListenerContainer> listenerContainers(
            KafkaListenerEndpointRegistry registry, List<String> groupIds) {
        var listeners = new ArrayList<MessageListenerContainer>();
        for (var container : containers(registry, groupIds)) {
            if (container instanceof ConcurrentMessageListenerContainer<?, ?> concurrent) {
                listeners.addAll(concurrent.getContainers());
            } else {
                listeners.add(container);
            }
        }
        return listeners;
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
