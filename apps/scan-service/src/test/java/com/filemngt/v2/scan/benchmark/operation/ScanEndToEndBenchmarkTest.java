package com.filemngt.v2.scan.benchmark.operation;

import static com.filemngt.v2.scan.benchmark.fixture.ScanEndToEndBenchmarkSettings.COMPLETION_SHARD_COUNT;
import static com.filemngt.v2.scan.benchmark.fixture.ScanEndToEndBenchmarkSettings.PIPELINE_TIMEOUT;
import static com.filemngt.v2.scan.benchmark.fixture.ScanEndToEndBenchmarkSettings.ROOT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogExistenceClient;
import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.filesystem.ScanFileCursorProvider;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.approval.ApprovalOperationService;
import com.filemngt.v2.scan.application.scan.ScanService;
import com.filemngt.v2.scan.benchmark.fixture.InMemoryScanFileCursor;
import com.filemngt.v2.scan.benchmark.fixture.ScanCoreBenchmarkDatabaseFixture;
import com.filemngt.v2.scan.benchmark.fixture.ScanEndToEndBenchmarkAwaiter;
import com.filemngt.v2.scan.benchmark.fixture.ScanEndToEndBenchmarkVerifier;
import com.filemngt.v2.scan.benchmark.fixture.SyntheticScanItemGenerator;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Combined Scan pipeline từ synthetic filesystem cursor tới final Kafka broker ACK.
 * Filesystem và Catalog HTTP là external boundary duy nhất được thay bằng deterministic test doubles.
 */
@Tag("benchmark")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        properties = {
            "scan.approval-operation.enabled=true",
            "scan.approval-operation.fixed-delay-ms=1",
            "scan.approval-operation.completion-shard-count=64",
            "scan.approval-operation.worker-concurrency=4",
            "scan.outbox.enabled=true",
            "scan.outbox.lane-relay-enabled=true",
            "scan.outbox.scheduler-delay-ms=1",
            "scan.outbox.lane-count=64",
            "scan.outbox.lane-worker-concurrency=4",
            "scan.outbox.lane-fetch-size=2000",
            "scan.outbox.lane-max-in-flight-events=5000",
            "scan.review-projection.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=30",
            "p6spy.enabled=false"
        })
@Import(ScanEndToEndBenchmarkTopicConfiguration.class)
class ScanEndToEndBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanEndToEndBenchmarkTest.class);
    private static final Instant BENCHMARK_TIMESTAMP = Instant.parse("2026-01-01T00:00:00Z");
    private static final Path EMPTY_ROOT = createEmptyRoot();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Autowired
    ScanService scans;

    @Autowired
    ScanRunRepository runs;

    @Autowired
    ApprovalOperationService operations;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ScanFileCursorProvider cursors;

    @MockitoBean
    CatalogRegistryClient catalogRegistry;

    @MockitoBean
    CatalogExistenceClient catalogExistence;

    private InMemoryScanFileCursor cursor;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("scan.roots[0].key", () -> ROOT_KEY);
        registry.add("scan.roots[0].path", EMPTY_ROOT::toString);
        registry.add("scan.roots[0].profile", () -> "JOKE_VIDEO");
    }

    @BeforeEach
    void resetDatabase() {
        ScanCoreBenchmarkDatabaseFixture.resetTables(jdbc);
        mockCatalogBoundaries();
    }

    @AfterAll
    static void deleteEmptyRoot() throws IOException {
        Files.deleteIfExists(EMPTY_ROOT);
    }

    @Test
    @Order(1)
    void measuresCombinedPipelineForTwentyFiveThousandInputRecords() {
        measureCombinedPipeline(25_000);
    }

    @Test
    @Order(2)
    void measuresCombinedPipelineForTwoHundredFiftyThousandInputRecords() {
        measureCombinedPipeline(250_000);
    }

    @Test
    @Order(3)
    void measuresCombinedPipelineForOneMillionInputRecords() {
        measureCombinedPipeline(1_000_000);
    }

    private void measureCombinedPipeline(int inputCount) {
        prepareInput(inputCount);
        long deadlineNanos = System.nanoTime() + PIPELINE_TIMEOUT.toNanos();
        long pipelineStarted = System.nanoTime();
        var scan = scans.start(ROOT_KEY, false);
        var stages = new ScanEndToEndBenchmarkAwaiter(jdbc, runs, operations, LOGGER);
        stages.scanCompleted(scan.id(), deadlineNanos);
        long scanCoreMillis = elapsedMillis(pipelineStarted);

        long approvalStarted = System.nanoTime();
        var operation = operations.accept(scan.id());
        stages.approvalCommitted(scan.id(), operation.operationId(), deadlineNanos);
        long approvalMillis = elapsedMillis(approvalStarted);
        stages.brokerAcknowledged(scan.id(), operation.operationId(), deadlineNanos);
        long approvalToAckMillis = elapsedMillis(approvalStarted);
        long totalMillis = elapsedMillis(pipelineStarted);

        assertThat(cursor.isClosed()).isTrue();
        var measurement = new ScanEndToEndBenchmarkVerifier.Measurement(
                scan.id(),
                operation.operationId(),
                inputCount,
                COMPLETION_SHARD_COUNT,
                scanCoreMillis,
                approvalMillis,
                approvalToAckMillis,
                totalMillis);
        var result = ScanEndToEndBenchmarkVerifier.assertDurableCompletion(jdbc, measurement);
        ScanEndToEndBenchmarkVerifier.log(LOGGER, result);
    }

    private void prepareInput(int inputCount) {
        var items = SyntheticScanItemGenerator.generateItems(
                ROOT_KEY, inputCount, 0.0, SyntheticScanItemGenerator.DEFAULT_TAGGED_RATE, BENCHMARK_TIMESTAMP);
        cursor = new InMemoryScanFileCursor(items);
        clearInvocations(cursors);
        when(cursors.open(any(Path.class), eq(ROOT_KEY))).thenReturn(cursor);
    }

    private void mockCatalogBoundaries() {
        var snapshot = new ScanRegistrySnapshot(
                100L, "JOKE", SyntheticScanItemGenerator.DEFAULT_STUDIOS, SyntheticScanItemGenerator.DEFAULT_TAGS);
        when(catalogRegistry.fetch(anyString())).thenReturn(Optional.of(snapshot));
        when(catalogExistence.classify(any(), anyList()))
                .thenAnswer(invocation -> allNewSubjects(invocation.getArgument(1)));
    }

    private Map<UUID, CatalogExistenceClient.Result> allNewSubjects(List<CatalogExistenceClient.Candidate> candidates) {
        Map<UUID, CatalogExistenceClient.Result> results = new HashMap<>(candidates.size());
        for (var candidate : candidates) {
            results.put(
                    candidate.clientRef(),
                    new CatalogExistenceClient.Result(
                            candidate.clientRef(),
                            CatalogExistenceClient.Classification.NEW_SUBJECT,
                            null,
                            null,
                            null));
        }
        return results;
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static Path createEmptyRoot() {
        try {
            return Files.createTempDirectory("scan-end-to-end-benchmark-root");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
