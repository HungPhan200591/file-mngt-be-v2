package com.filemngt.v2.catalog.benchmark.operation;

import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.ASSIGNMENT_TIMEOUT;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.COMPLETION_GROUP;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.COMPLETION_TOPIC;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.DISCOVERY_PARTITIONS;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.DISCOVERY_TOPIC;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.OPERATION_COMPLETION_TIMEOUT;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.OPERATION_CONCURRENCY;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.OPERATION_GROUP;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.PRODUCE_BATCH_SIZE;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.WATERMARK_GROUP;
import static com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkSettings.WATERMARK_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.application.operation.CatalogOperationFinalizerTelemetry;
import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkDiagnostics;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkVerifier;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationKafkaBenchmarkSupport;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationKafkaConsumerControl;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerConfigUtils;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
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
 * Combined FT-059 benchmark: Kafka receive, logical-shard marker, bounded-page finalizer, operation relay và broker
 * acknowledgement.
 * Đồng hồ ngoài chạy từ lúc resume consumer đến final watermark broker-ack; đồng hồ durable chạy từ input đầu tiên
 * được Catalog persist tới timestamp broker-ack được relay mark. Seed và assignment luôn ở ngoài hai clock.
 */
@Tag("benchmark")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.outbox.operation-relay.enabled=true",
            "catalog.outbox.operation-relay.worker-count=4",
            "catalog.outbox.operation-relay.fetch-size=2000",
            "catalog.outbox.operation-relay.max-in-flight=500",
            "catalog.outbox.operation-relay.scheduler-delay-ms=1",
            "catalog.operation.finalizer-enabled=true",
            "catalog.operation.seal-enabled=false",
            "catalog.operation.shard-seal-enabled=true",
            "catalog.operation.default-processing-version=59",
            "catalog.operation.worker-count=4",
            "catalog.operation.seal-batch-size=64",
            "catalog.operation.completion-shard-delay-ms=1",
            "catalog.operation.subject-page-size=500",
            "catalog.operation.finalizer-delay-ms=1",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=true",
            "catalog.kafka.operation-consumer.topic-provisioning-enabled=false",
            "catalog.kafka.operation-consumer.concurrency=4",
            "catalog.kafka.operation-consumer.max-poll-records=2000",
            "catalog.kafka.operation-consumer.slice-records=2000",
            "spring.kafka.consumer.properties.max.poll.records=2000",
            "catalog.kafka.dlt-observer.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=30",
            "p6spy.enabled=false"
        })
@Import(CatalogOperationEndToEndBenchmarkTopicConfiguration.class)
class CatalogOperationEndToEndBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationEndToEndBenchmarkTest.class);
    // Chỉ giới hạn đoạn Catalog xử lý event sau resume; không tính fixture seed hay assignment.

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CatalogOperationIngestTelemetry ingestTelemetry;

    @Autowired
    CatalogOperationFinalizerTelemetry finalizerTelemetry;

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
    void measuresCombinedPipelineForTwentyFiveThousandInputRecords() {
        measureCombinedPipeline(25_000, OPERATION_COMPLETION_TIMEOUT);
    }

    @Test
    @Order(2)
    void measuresCombinedPipelineForTwoHundredFiftyThousandInputRecords() {
        measureCombinedPipeline(250_000, OPERATION_COMPLETION_TIMEOUT);
    }

    @Test
    @Order(3)
    void measuresCombinedPipelineForOneMillionInputRecords() {
        measureCombinedPipeline(1_000_000, OPERATION_COMPLETION_TIMEOUT);
    }

    private void measureCombinedPipeline(int eventCount, Duration completionTimeout) {
        CatalogOperationKafkaConsumerControl.awaitAssignments(
                listenerRegistry(),
                OPERATION_GROUP,
                DISCOVERY_TOPIC,
                DISCOVERY_PARTITIONS,
                OPERATION_CONCURRENCY,
                WATERMARK_GROUP,
                WATERMARK_TOPIC,
                COMPLETION_GROUP,
                COMPLETION_TOPIC,
                DISCOVERY_PARTITIONS,
                ASSIGNMENT_TIMEOUT);
        pauseInputConsumers();
        long discoverySeedMs = CatalogOperationKafkaBenchmarkSupport.seedDiscoveries(
                kafka, json, DISCOVERY_TOPIC, eventCount, PRODUCE_BATCH_SIZE);
        long completionSeedMs = CatalogOperationKafkaBenchmarkSupport.seedApprovalShardCompletedMarkers(
                kafka, json, COMPLETION_TOPIC, eventCount);
        long watermarkSeedMs = CatalogOperationKafkaBenchmarkSupport.seedApprovalCommittedWatermark(
                kafka, json, WATERMARK_TOPIC, eventCount);

        long resumeStarted = System.nanoTime();
        resumeInputConsumers();
        long firstPersistedInputAt = awaitFirstInputPersisted(completionTimeout);
        awaitAllInputPersisted(eventCount, completionTimeout);
        awaitCatalogCommitted(completionTimeout);
        long resumeToFinalAckMs = CatalogOperationEndToEndBenchmarkVerifier.elapsedMillis(resumeStarted);
        long firstPersistToFinalAckMs = CatalogOperationEndToEndBenchmarkVerifier.elapsedMillis(firstPersistedInputAt);
        pauseInputConsumers();

        var result = CatalogOperationEndToEndBenchmarkVerifier.assertDurableCompletion(
                jdbc, eventCount, resumeToFinalAckMs, firstPersistToFinalAckMs);
        CatalogOperationEndToEndBenchmarkVerifier.logResult(
                LOGGER,
                eventCount,
                discoverySeedMs,
                completionSeedMs,
                watermarkSeedMs,
                result,
                ingestTelemetry.snapshot(),
                finalizerTelemetry.snapshot());
    }

    private void pauseInputConsumers() {
        CatalogOperationKafkaConsumerControl.pause(
                listenerRegistry(), List.of(OPERATION_GROUP, WATERMARK_GROUP, COMPLETION_GROUP), ASSIGNMENT_TIMEOUT);
    }

    private void resumeInputConsumers() {
        var groupIds = List.of(OPERATION_GROUP, WATERMARK_GROUP, COMPLETION_GROUP);
        CatalogOperationKafkaConsumerControl.resume(listenerRegistry(), groupIds);
        CatalogOperationKafkaConsumerControl.awaitResumed(listenerRegistry(), groupIds, ASSIGNMENT_TIMEOUT);
    }

    private void awaitAllInputPersisted(int eventCount, Duration timeout) {
        try {
            org.awaitility.Awaitility.await()
                    .alias("Catalog combined benchmark ingests all discovery input")
                    .pollInterval(Duration.ofMillis(50))
                    .atMost(timeout)
                    .untilAsserted(() -> assertThat(ingestedRecordCount()).isEqualTo(eventCount));
        } catch (ConditionTimeoutException exception) {
            LOGGER.error(
                    "Catalog combined benchmark input persistence timeout: {}; ingest={}; finalizer={}",
                    CatalogOperationEndToEndBenchmarkDiagnostics.describe(
                            jdbc, CatalogOperationBenchmarkFixture.operationId()),
                    ingestTelemetry.snapshot(),
                    finalizerTelemetry.snapshot());
            throw exception;
        }
    }

    private long awaitFirstInputPersisted(Duration timeout) {
        var firstPersistedAt = new AtomicLong();
        org.awaitility.Awaitility.await()
                .alias("Catalog combined benchmark persists the first discovery input")
                .pollInterval(Duration.ofMillis(5))
                .atMost(timeout)
                .until(() -> {
                    if (ingestedRecordCount() == 0) return false;
                    firstPersistedAt.compareAndSet(0, System.nanoTime());
                    return true;
                });
        return firstPersistedAt.get();
    }

    private void awaitCatalogCommitted(Duration timeout) {
        try {
            org.awaitility.Awaitility.await()
                    .alias("Catalog operation final watermark broker acknowledgement")
                    .pollInterval(Duration.ofMillis(50))
                    .atMost(timeout)
                    .untilAsserted(() -> assertThat(operationStatus()).isEqualTo("CATALOG_COMMITTED"));
        } catch (ConditionTimeoutException exception) {
            LOGGER.error(
                    "Catalog combined benchmark terminal timeout: {}; ingest={}; finalizer={}",
                    CatalogOperationEndToEndBenchmarkDiagnostics.describe(
                            jdbc, CatalogOperationBenchmarkFixture.operationId()),
                    ingestTelemetry.snapshot(),
                    finalizerTelemetry.snapshot());
            throw exception;
        }
    }

    private String operationStatus() {
        var statuses = jdbc.queryForList(
                "select status from catalog_approval_operation where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    private long ingestedRecordCount() {
        Long count = jdbc.queryForObject(
                "select count(*) from catalog_operation_discovery_input where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return count == null ? 0 : count;
    }

    private KafkaListenerEndpointRegistry listenerRegistry() {
        return applicationContext.getBean(
                KafkaListenerConfigUtils.KAFKA_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME,
                KafkaListenerEndpointRegistry.class);
    }

    private void resetState() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
        ingestTelemetry.reset();
        finalizerTelemetry.reset();
    }
}
