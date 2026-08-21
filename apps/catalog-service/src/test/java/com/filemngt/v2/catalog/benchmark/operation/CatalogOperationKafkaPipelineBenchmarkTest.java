package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import java.time.Duration;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Benchmark 100% Production Pipeline thật qua Kafka Broker Testcontainer:
 * Spring Boot tự khởi động 4 Kafka Consumer Threads thật (CatalogOperationBatchConsumer)
 * lắng nghe topic "media.file.discovered.v2" với 8 partitions, tự động deserialize batch, nạp vào Stage Store.
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
            "catalog.kafka.operation-consumer.slice-records=5000",
            "catalog.kafka.dlt-observer.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=30",
            "p6spy.enabled=false"
        })
@Import(CatalogOperationKafkaPipelineBenchmarkTest.BenchmarkKafkaTopicConfiguration.class)
class CatalogOperationKafkaPipelineBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationKafkaPipelineBenchmarkTest.class);
    private static final String TOPIC = "media.file.discovered.v2";
    private static final int PARTITION_COUNT = 8;

    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES =
            (PostgreSQLContainer) new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"))
                    .withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw"))
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
    org.springframework.kafka.config.KafkaListenerEndpointRegistry listenerRegistry;

    @BeforeEach
    void resetDatabase() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
        if (telemetry != null) telemetry.reset();
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
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void measuresRealKafkaPipelineForTwentyFiveThousandEvents() {
        measureKafkaPipeline(25_000);
    }

    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void measuresRealKafkaPipelineForOneMillionEvents() {
        measureKafkaPipeline(1_000_000);
    }

    private void measureKafkaPipeline(int eventCount) {
        // Tạm dừng consumer để nạp sẵn toàn bộ dữ liệu vào Kafka Broker
        listenerRegistry.stop();

        // 1. Seed toàn bộ events vào 8 Partitions của Kafka Broker
        try (var producerPool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            int batchSize = 10_000;
            for (int start = 0; start < eventCount; start += batchSize) {
                int sliceStart = start;
                int count = Math.min(batchSize, eventCount - start);
                producerPool.submit(() -> {
                    for (int i = sliceStart; i < sliceStart + count; i++) {
                        var event = CatalogOperationBenchmarkFixture.discoveryEvent(i);
                        String payload = json.writeValueAsString(event);
                        kafkaTemplate.send(
                                TOPIC, i % PARTITION_COUNT, event.eventId().toString(), payload);
                    }
                });
            }
        }
        kafkaTemplate.flush();

        // 2. BẬT ĐỒNG HỒ BẤM GIỜ: Kích hoạt 8 Consumer Threads của Spring Boot tiêu hóa message vào DB
        long processingStarted = System.nanoTime();
        listenerRegistry.start();

        long expectedSubjects = (eventCount + CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT - 1L)
                / CatalogOperationBenchmarkFixture.ASSETS_PER_SUBJECT;

        await().alias("Kafka consumers ingest all records into database")
                .pollInterval(Duration.ofMillis(50))
                .atMost(Duration.ofMinutes(4))
                .untilAsserted(() -> {
                    assertThat(
                                    count(
                                            "select received_record_count from catalog_approval_operation where operation_id = ?"))
                            .isEqualTo(eventCount);
                });

        long processingElapsedMs = (System.nanoTime() - processingStarted) / 1_000_000L;

        assertThat(count("select count(*) from catalog_operation_subject where operation_id = ?"))
                .isEqualTo(expectedSubjects);
        assertThat(count("select count(*) from catalog_discovery_stage where operation_id = ?"))
                .isEqualTo(eventCount);

        var snap = telemetry != null ? telemetry.snapshot() : null;
        LOGGER.info(
                "FT-055 Full Kafka Production Consumer: events={}, subjects={}, partitions={}, processingMs={}, throughputPerSecond={}\n  -> {}",
                eventCount,
                expectedSubjects,
                PARTITION_COUNT,
                processingElapsedMs,
                throughput(eventCount, processingElapsedMs),
                snap);
    }

    private long count(String sql) {
        var results = jdbc.queryForList(sql, Long.class, CatalogOperationBenchmarkFixture.operationId());
        return results.isEmpty() || results.getFirst() == null ? 0L : results.getFirst();
    }

    private long throughput(int eventCount, long elapsedMillis) {
        return elapsedMillis == 0 ? 0 : Math.round(eventCount * 1_000.0 / elapsedMillis);
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
