package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerConfigUtils;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Benchmark backlog-drain của {@code CatalogOperationBatchConsumer} qua Kafka Testcontainer.
 * Topology chủ đích: 8 partition / 8 consumer / {@code max.poll.records=5000} /
 * {@code slice-records=5000}; không phải default production ({@code concurrency=4}, poll/slice=2000).
 * {@code drainMs} bắt đầu lúc {@code resume()} sau khi consumer đã assigned và pause — không gồm
 * rebalance, seed Kafka hay warm-up. Không phải gate D1 isolated ingest và không phải SLO
 * {@code QUERY_DB_READY}.
 */
@Tag("benchmark")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=false",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=true",
            "catalog.kafka.operation-consumer.concurrency=8",
            "catalog.kafka.operation-consumer.max-poll-records=5000",
            "catalog.kafka.operation-consumer.slice-records=5000",
            "spring.kafka.consumer.properties.max.poll.records=5000",
            "catalog.kafka.dlt-observer.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=30",
            "p6spy.enabled=false"
        })
@Import(CatalogOperationKafkaPipelineBenchmarkTest.BenchmarkKafkaTopicConfiguration.class)
class CatalogOperationKafkaPipelineBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationKafkaPipelineBenchmarkTest.class);
    private static final String TOPIC = "media.file.discovered.v2";
    private static final String OPERATION_GROUP = "catalog-operation-coalescing";
    private static final int PARTITION_COUNT = 8;
    private static final int CONCURRENCY = 8;
    private static final int MAX_POLL_RECORDS = 5_000;
    private static final int SLICE_RECORDS = 5_000;
    private static final int HIKARI_POOL_SIZE = 30;
    private static final int PRODUCE_BATCH_SIZE = 10_000;
    private static final Duration ASSIGNMENT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration WARM_UP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CALIBRATION_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration QUALIFICATION_TIMEOUT = Duration.ofMinutes(4);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"))
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
            .withCommand(
                    "postgres",
                    "-c",
                    "fsync=off",
                    "-c",
                    "synchronous_commit=off",
                    "-c",
                    "full_page_writes=off",
                    "-c",
                    "shared_buffers=512MB",
                    "-c",
                    "work_mem=32MB");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CatalogOperationIngestTelemetry telemetry;

    @Autowired
    ApplicationContext applicationContext;

    @BeforeEach
    void resetDatabase() {
        resetState();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    @Order(1)
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresRealKafkaPipelineForTwentyFiveThousandEvents() {
        measureKafkaPipeline(25_000, CALIBRATION_TIMEOUT);
    }

    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresRealKafkaPipelineForOneMillionEvents() {
        measureKafkaPipeline(1_000_000, QUALIFICATION_TIMEOUT);
    }

    private void measureKafkaPipeline(int eventCount, Duration drainTimeout) {
        long assignmentStarted = System.nanoTime();
        awaitAssignment();
        long assignmentMs = elapsedMillis(assignmentStarted);
        pauseOperationConsumers();
        warmUpAndReset();
        long produceMs = seedEvents(eventCount);
        long drainStarted = System.nanoTime();
        resumeOperationConsumers();
        awaitIngested(eventCount, drainTimeout);
        long drainMs = elapsedMillis(drainStarted);
        pauseOperationConsumers();
        assertDurableCounts(eventCount);
        logResult(eventCount, assignmentMs, produceMs, drainMs);
    }

    private void warmUpAndReset() {
        seedEvents(CatalogOperationBenchmarkFixture.WARM_UP_EVENTS);
        resumeOperationConsumers();
        awaitIngested(CatalogOperationBenchmarkFixture.WARM_UP_EVENTS, WARM_UP_TIMEOUT);
        pauseOperationConsumers();
        resetState();
    }

    private long seedEvents(int eventCount) {
        long started = System.nanoTime();
        try (var producerPool = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (int start = 0; start < eventCount; start += PRODUCE_BATCH_SIZE) {
                int sliceStart = start;
                int count = Math.min(PRODUCE_BATCH_SIZE, eventCount - start);
                tasks.add(producerPool.submit(() -> publishSlice(sliceStart, count)));
            }
            for (var task : tasks) {
                task.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka seed interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Kafka seed failed", exception.getCause());
        }
        kafkaTemplate.flush();
        return elapsedMillis(started);
    }

    private void publishSlice(int sliceStart, int count) {
        var sends = new ArrayList<CompletableFuture<?>>(count);
        for (int index = sliceStart; index < sliceStart + count; index++) {
            var event = CatalogOperationBenchmarkFixture.discoveryEvent(index);
            sends.add(kafkaTemplate.send(
                    TOPIC, CatalogOperationBenchmarkFixture.partitionKey(event), json.writeValueAsString(event)));
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
    }

    private void awaitAssignment() {
        await().alias("operation consumers assigned to all discovery partitions")
                .pollInterval(Duration.ofMillis(50))
                .atMost(ASSIGNMENT_TIMEOUT)
                .untilAsserted(() -> assertThat(assignedDiscoveryPartitions()).isEqualTo(PARTITION_COUNT));
    }

    /** Pause cooperative: consumer vẫn ở group, không rebalance; có hiệu lực trước poll kế tiếp. */
    private void pauseOperationConsumers() {
        for (var container : operationContainers()) {
            container.pause();
        }
        await().alias("operation consumers paused without leaving the group")
                .pollInterval(Duration.ofMillis(20))
                .atMost(ASSIGNMENT_TIMEOUT)
                .untilAsserted(() -> assertThat(operationContainers())
                        .isNotEmpty()
                        .allMatch(MessageListenerContainer::isContainerPaused));
    }

    private void resumeOperationConsumers() {
        for (var container : operationContainers()) {
            container.resume();
        }
    }

    private void awaitIngested(int eventCount, Duration timeout) {
        await().alias("Kafka consumers ingest all records into database")
                .pollInterval(Duration.ofMillis(50))
                .atMost(timeout)
                .untilAsserted(() -> assertThat(count(
                                "select coalesce(sum(inserted_record_count), 0) from catalog_operation_ingest_partition where operation_id = ?"))
                        .isEqualTo(eventCount));
    }

    private void assertDurableCounts(int eventCount) {
        assertThat(count("select count(*) from catalog_operation_discovery_input where operation_id = ?"))
                .isEqualTo(eventCount);
    }

    private void logResult(int eventCount, long assignmentMs, long produceMs, long drainMs) {
        var snap = telemetry != null ? telemetry.snapshot() : null;
        LOGGER.info(
                "FT-055 Kafka backlog drain: events={}, subjects={}, partitions={}, concurrency={}, "
                        + "maxPollRecords={}, sliceRecords={}, hikariPool={}, assignmentMs={}, produceMs={}, "
                        + "drainMs={}, throughputPerSecond={}\n  -> {}",
                eventCount,
                expectedSubjects(eventCount),
                PARTITION_COUNT,
                CONCURRENCY,
                MAX_POLL_RECORDS,
                SLICE_RECORDS,
                HIKARI_POOL_SIZE,
                assignmentMs,
                produceMs,
                drainMs,
                throughput(eventCount, drainMs),
                snap);
    }

    private KafkaListenerEndpointRegistry listenerRegistry() {
        return applicationContext.getBean(
                KafkaListenerConfigUtils.KAFKA_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME,
                KafkaListenerEndpointRegistry.class);
    }

    private List<MessageListenerContainer> operationContainers() {
        var containers = new ArrayList<MessageListenerContainer>();
        for (var container : listenerRegistry().getListenerContainers()) {
            if (OPERATION_GROUP.equals(container.getGroupId())) {
                containers.add(container);
            }
        }
        return containers;
    }

    private int assignedDiscoveryPartitions() {
        int assigned = 0;
        for (var container : operationContainers()) {
            var partitions = container.getAssignedPartitions();
            if (partitions == null) {
                continue;
            }
            for (var partition : partitions) {
                if (TOPIC.equals(partition.topic())) {
                    assigned++;
                }
            }
        }
        return assigned;
    }

    private void resetState() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
        if (telemetry != null) {
            telemetry.reset();
        }
    }

    private long count(String sql) {
        var results = jdbc.queryForList(sql, Long.class, CatalogOperationBenchmarkFixture.operationId());
        return results.isEmpty() || results.getFirst() == null ? 0L : results.getFirst();
    }

    private static long expectedSubjects(int eventCount) {
        return (eventCount + CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT - 1L)
                / CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT;
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(1, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private static long throughput(int eventCount, long elapsedMillis) {
        return Math.round(eventCount * 1_000.0 / elapsedMillis);
    }

    @TestConfiguration
    static class BenchmarkKafkaTopicConfiguration {
        @Bean
        NewTopic mediaFileDiscoveredTopic() {
            return TopicBuilder.name(TOPIC)
                    .partitions(PARTITION_COUNT)
                    .replicas(1)
                    .build();
        }
    }
}
