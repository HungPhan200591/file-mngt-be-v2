package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/** Kafka failure matrix tối thiểu: poison DLT đúng partition và transient DB replay thành công. */
@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=false",
            "catalog.operation.seal-enabled=false",
            "catalog.operation.watchdog-enabled=false",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=true",
            "catalog.kafka.operation-consumer.concurrency=4",
            "catalog.kafka.operation-consumer.topic-provisioning-enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "p6spy.enabled=false"
        })
@Import(CatalogOperationEndToEndBenchmarkTopicConfiguration.class)
class CatalogOperationKafkaReliabilityIT {
    private static final String INPUT_TOPIC = "media.file.discovered.v2";
    private static final String DLT_TOPIC = INPUT_TOPIC + ".DLT";

    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ObjectMapper json;

    @Autowired
    KafkaListenerEndpointRegistry listeners;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    CatalogOperationStageStore stage;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeEach
    void resetState() {
        reset(stage);
        CatalogOperationBenchmarkFixture.reset(jdbc);
        awaitInputAssignments();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void malformedRecordPublishesToMatchingDltPartitionWithoutRetry(int partition) throws Exception {
        String key = "poison-" + UUID.randomUUID();
        kafka.send(new ProducerRecord<>(INPUT_TOPIC, partition, key, "{\"eventType\":\"unsupported.v1\"}"))
                .get();

        ConsumerRecord<String, String> deadLetter = pollDlt(partition, key, Duration.ofSeconds(20));

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.partition()).isEqualTo(partition);
        assertThat(headerInt(deadLetter, KafkaHeaders.DLT_ORIGINAL_PARTITION)).isEqualTo(partition);
        verifyNoInteractions(stage);
    }

    @Test
    void transientDeadlockRetriesSameRecordAndDoesNotPublishDlt() throws Exception {
        String key = "transient-" + UUID.randomUUID();
        doThrow(new DeadlockLoserDataAccessException("deadlock", null))
                .doReturn(1)
                .when(stage)
                .ingest(anyList(), anyList());
        String payload = json.writeValueAsString(CatalogOperationBenchmarkFixture.discoveryEvent(0));

        kafka.send(new ProducerRecord<>(INPUT_TOPIC, 2, key, payload)).get();

        verify(stage, timeout(10_000).atLeast(2)).ingest(anyList(), anyList());
        assertThat(pollDlt(2, key, Duration.ofSeconds(2))).isNull();
    }

    private void awaitInputAssignments() {
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(assignedInputPartitionCount()).isEqualTo(4));
    }

    private long assignedInputPartitionCount() {
        return listeners.getListenerContainers().stream()
                .filter(container -> "catalog-operation-coalescing".equals(container.getGroupId()))
                .filter(container -> container.getAssignedPartitions() != null)
                .flatMap(container -> container.getAssignedPartitions().stream())
                .filter(partition -> INPUT_TOPIC.equals(partition.topic()))
                .distinct()
                .count();
    }

    private ConsumerRecord<String, String> pollDlt(int partition, String key, Duration timeout) {
        Properties properties = consumerProperties();
        try (var consumer = new KafkaConsumer<>(properties, new StringDeserializer(), new StringDeserializer())) {
            var topicPartition = new TopicPartition(DLT_TOPIC, partition);
            consumer.assign(List.of(topicPartition));
            consumer.seekToBeginning(List.of(topicPartition));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (key.equals(record.key())) return record;
                }
            }
            return null;
        }
    }

    private Properties consumerProperties() {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "catalog-reliability-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return properties;
    }

    private int headerInt(ConsumerRecord<String, String> record, String name) {
        return ByteBuffer.wrap(record.headers().lastHeader(name).value()).getInt();
    }
}
