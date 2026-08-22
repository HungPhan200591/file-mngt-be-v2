package com.filemngt.v2.catalog.benchmark.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.catalog.application.operation.CatalogOperationFinalizerTelemetry;
import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationEndToEndBenchmarkVerifier;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationKafkaBenchmarkSupport;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationKafkaConsumerControl;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
 * Combined FT-057 benchmark: Kafka receive, typed stage, finalizer, operation relay và Kafka broker acknowledgement.
 * Đồng hồ ngoài chạy từ lúc resume consumer đến final watermark broker-ack; đồng hồ durable chạy từ input đầu tiên
 * được Catalog persist tới timestamp broker-ack được relay mark. Seed, assignment và warm-up luôn ở ngoài hai clock.
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
            "catalog.operation.worker-count=4",
            "catalog.operation.reconcile-unit-count=16",
            "catalog.operation.finalizer-delay-ms=1",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=true",
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
    private static final String DISCOVERY_TOPIC = "media.file.discovered.v2";
    private static final String WATERMARK_TOPIC = "media.approval.watermark.v1";
    private static final String OPERATION_GROUP = "catalog-operation-coalescing";
    private static final String WATERMARK_GROUP = "catalog-operation-watermark";
    private static final int DISCOVERY_PARTITIONS = 4;
    private static final int OPERATION_CONCURRENCY = 4;
    private static final int PRODUCE_BATCH_SIZE = 10_000;
    private static final Duration ASSIGNMENT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration WARM_UP_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration CALIBRATION_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration QUALIFICATION_TIMEOUT = Duration.ofMinutes(5);
    private static final long MINIMUM_TARGET_RECORDS_PER_SECOND = 30_000;
    private static final long STRETCH_TARGET_RECORDS_PER_SECOND = 40_000;

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
    @Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresCombinedPipelineForTwentyFiveThousandInputRecords() {
        measureCombinedPipeline(25_000, CALIBRATION_TIMEOUT);
    }

    @Test
    @Order(2)
    @Timeout(value = 6, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresCombinedPipelineForOneMillionInputRecords() {
        measureCombinedPipeline(1_000_000, QUALIFICATION_TIMEOUT);
    }

    private void measureCombinedPipeline(int eventCount, Duration completionTimeout) {
        CatalogOperationKafkaConsumerControl.awaitAssignments(
                listenerRegistry(),
                OPERATION_GROUP,
                DISCOVERY_TOPIC,
                DISCOVERY_PARTITIONS,
                WATERMARK_GROUP,
                WATERMARK_TOPIC,
                ASSIGNMENT_TIMEOUT);
        pauseInputConsumers();
        warmUpAndReset();
        long discoverySeedMs = CatalogOperationKafkaBenchmarkSupport.seedDiscoveries(
                kafka, json, DISCOVERY_TOPIC, eventCount, PRODUCE_BATCH_SIZE);
        long watermarkSeedMs = CatalogOperationKafkaBenchmarkSupport.seedApprovalCommittedWatermark(
                kafka, json, WATERMARK_TOPIC, eventCount);

        long resumeStarted = System.nanoTime();
        resumeInputConsumers();
        awaitCatalogCommitted(completionTimeout);
        long resumeToFinalAckMs = elapsedMillis(resumeStarted);
        pauseInputConsumers();

        var result =
                CatalogOperationEndToEndBenchmarkVerifier.assertDurableCompletion(jdbc, eventCount, resumeToFinalAckMs);
        logResult(eventCount, discoverySeedMs, watermarkSeedMs, result);
    }

    private void warmUpAndReset() {
        CatalogOperationKafkaBenchmarkSupport.seedDiscoveries(
                kafka, json, DISCOVERY_TOPIC, CatalogOperationBenchmarkFixture.WARM_UP_EVENTS, PRODUCE_BATCH_SIZE);
        CatalogOperationKafkaBenchmarkSupport.seedApprovalCommittedWatermark(
                kafka, json, WATERMARK_TOPIC, CatalogOperationBenchmarkFixture.WARM_UP_EVENTS);
        resumeInputConsumers();
        awaitCatalogCommitted(WARM_UP_TIMEOUT);
        pauseInputConsumers();
        resetState();
    }

    private void pauseInputConsumers() {
        CatalogOperationKafkaConsumerControl.pause(
                listenerRegistry(), List.of(OPERATION_GROUP, WATERMARK_GROUP), ASSIGNMENT_TIMEOUT);
    }

    private void resumeInputConsumers() {
        CatalogOperationKafkaConsumerControl.resume(listenerRegistry(), List.of(OPERATION_GROUP, WATERMARK_GROUP));
    }

    private void awaitCatalogCommitted(Duration timeout) {
        org.awaitility.Awaitility.await()
                .alias("Catalog operation final watermark broker acknowledgement")
                .pollInterval(Duration.ofMillis(50))
                .atMost(timeout)
                .untilAsserted(() -> assertThat(operationStatus()).isEqualTo("CATALOG_COMMITTED"));
    }

    private void logResult(
            int eventCount,
            long discoverySeedMs,
            long watermarkSeedMs,
            CatalogOperationEndToEndBenchmarkVerifier.Result result) {
        long resumeRecordsPerSecond = throughput(eventCount, result.resumeToFinalAckMs());
        long durableRecordsPerSecond = throughput(eventCount, result.firstPersistToFinalAckMs());
        LOGGER.info(
                "FT-057 combined Catalog pipeline: events={}, subjects={}, discoveryPartitions={}, "
                        + "operationConcurrency={}, discoverySeedMs={}, watermarkSeedMs={}, resumeToFinalAckMs={}, "
                        + "firstPersistToFinalAckMs={}, resumeRecordsPerSecond={}, durableRecordsPerSecond={}, "
                        + "minimumTargetMet={}, stretchTargetMet={}\n  -> ingest={} finalizer={}",
                eventCount,
                CatalogOperationBenchmarkFixture.expectedSubjects(eventCount),
                DISCOVERY_PARTITIONS,
                OPERATION_CONCURRENCY,
                discoverySeedMs,
                watermarkSeedMs,
                result.resumeToFinalAckMs(),
                result.firstPersistToFinalAckMs(),
                resumeRecordsPerSecond,
                durableRecordsPerSecond,
                resumeRecordsPerSecond >= MINIMUM_TARGET_RECORDS_PER_SECOND,
                resumeRecordsPerSecond >= STRETCH_TARGET_RECORDS_PER_SECOND,
                ingestTelemetry.snapshot(),
                finalizerTelemetry.snapshot());
    }

    private String operationStatus() {
        var statuses = jdbc.queryForList(
                "select status from catalog_approval_operation where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
        return statuses.isEmpty() ? null : statuses.getFirst();
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

    private static long elapsedMillis(long startedNanos) {
        return Math.max(1, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private static long throughput(int eventCount, long elapsedMillis) {
        return Math.round(eventCount * 1_000.0 / elapsedMillis);
    }
}
