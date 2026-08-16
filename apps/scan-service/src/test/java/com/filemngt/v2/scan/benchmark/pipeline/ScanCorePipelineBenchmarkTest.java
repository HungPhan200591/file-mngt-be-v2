package com.filemngt.v2.scan.benchmark.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogExistenceClient;
import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.filesystem.ScanFileCursorProvider;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import com.filemngt.v2.scan.application.scan.ScanService;
import com.filemngt.v2.scan.benchmark.fixture.InMemoryScanFileCursor;
import com.filemngt.v2.scan.benchmark.fixture.SyntheticScanItemGenerator;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Đo Scan Service core pipeline từ {@link ScanService#start(String, boolean)} tới terminal state.
 *
 * <p>Phép đo loại filesystem và Catalog I/O bằng in-memory cursor/Mockito. Phạm vi còn lại gồm production
 * orchestration, parsing, reconciliation, transaction và PostgreSQL persistence của Scan Service.
 *
 * <p>Chạy từ project root:
 * {@code mvn test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest}
 */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(
        properties = {
            "scan.outbox.enabled=false",
            "scan.review-projection.enabled=false",
            "spring.task.scheduling.enabled=false"
        })
class ScanCorePipelineBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanCorePipelineBenchmarkTest.class);
    private static final String ROOT_KEY = "benchmark-root";
    private static final int FILE_COUNT = 1_000_000;
    private static final long EXPECTED_ISSUE_COUNT =
            Math.round(FILE_COUNT * SyntheticScanItemGenerator.DEFAULT_ISSUE_RATE);
    private static final long EXPECTED_PROPOSAL_COUNT = FILE_COUNT - EXPECTED_ISSUE_COUNT;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private static final Duration TERMINAL_TIMEOUT =
            Duration.ofSeconds(Long.getLong("benchmark.scan-core.timeout-seconds", 300L));
    private static final Path EMPTY_BENCHMARK_ROOT = createEmptyRoot();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    private ScanService scanService;

    @Autowired
    private ScanRunRepository runs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ScanFileCursorProvider cursorProvider;

    @MockitoBean
    private CatalogRegistryClient catalogClient;

    @MockitoBean
    private CatalogExistenceClient catalogExistenceClient;

    private InMemoryScanFileCursor cursor;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("scan.roots[0].key", () -> ROOT_KEY);
        registry.add("scan.roots[0].path", EMPTY_BENCHMARK_ROOT::toString);
        registry.add("scan.roots[0].profile", () -> "JOKE_VIDEO");
    }

    @BeforeEach
    void setUp() {
        resetTables();
        mockCatalogIo();
        List<ScanInventoryItem> items = SyntheticScanItemGenerator.generateItems(ROOT_KEY, FILE_COUNT);
        cursor = new InMemoryScanFileCursor(items);
        Mockito.when(cursorProvider.open(Mockito.any(Path.class), Mockito.eq(ROOT_KEY)))
                .thenReturn(cursor);
    }

    @AfterEach
    void tearDown() {
        resetTables();
    }

    @AfterAll
    static void deleteEmptyRoot() throws IOException {
        Files.deleteIfExists(EMPTY_BENCHMARK_ROOT);
    }

    @Test
    @DisplayName("Scan core xử lý 1M synthetic items, không gồm filesystem và Catalog I/O")
    void measuresScanCorePipeline() {
        LOGGER.info("Bắt đầu scan-core benchmark: files={}, excluded=filesystem,catalog-io", FILE_COUNT);
        long startedNanos = System.nanoTime();

        ScanRunView view = scanService.start(ROOT_KEY, false);
        ScanRunEntity run = awaitTerminal(view.id());

        long durationMillis = elapsedMillis(startedNanos);
        logResult(run, durationMillis);
        assertCompletedPipeline(run);
    }

    private void mockCatalogIo() {
        var snapshot = new ScanRegistrySnapshot(
                100L, "JOKE", SyntheticScanItemGenerator.DEFAULT_STUDIOS, SyntheticScanItemGenerator.DEFAULT_TAGS);
        Mockito.when(catalogClient.fetch(Mockito.anyString())).thenReturn(Optional.of(snapshot));
        Mockito.when(catalogExistenceClient.classify(Mockito.any(), Mockito.anyList()))
                .thenAnswer(invocation -> allNewSubjects(invocation.getArgument(1)));
    }

    private static Map<UUID, CatalogExistenceClient.Result> allNewSubjects(
            List<CatalogExistenceClient.Candidate> candidates) {
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

    private ScanRunEntity awaitTerminal(UUID runId) {
        await().alias("scan-core terminal state")
                .pollInterval(POLL_INTERVAL)
                .atMost(TERMINAL_TIMEOUT)
                .untilAsserted(() -> {
                    var current = runs.findById(runId).orElseThrow();
                    assertThat(current.status())
                            .withFailMessage(
                                    "Run vẫn RUNNING: runId=%s, scanned=%d, error=%s",
                                    runId, current.scannedFileCount(), current.lastError())
                            .isNotEqualTo(ScanRunStatus.RUNNING);
                });
        return runs.findById(runId).orElseThrow();
    }

    private void assertCompletedPipeline(ScanRunEntity run) {
        assertThat(run.status())
                .withFailMessage("Scan core thất bại: %s", run.lastError())
                .isEqualTo(ScanRunStatus.COMPLETED);
        assertThat(run.scannedFileCount()).isEqualTo(FILE_COUNT);
        assertThat(run.changedFileCount()).isEqualTo((long) FILE_COUNT);
        assertThat(run.reconciledFileCount()).isEqualTo((long) FILE_COUNT);
        assertThat(run.proposalCount()).isEqualTo(EXPECTED_PROPOSAL_COUNT);
        assertThat(run.issueCount()).isEqualTo(EXPECTED_ISSUE_COUNT);
        assertPersistedRows();
        assertThat(cursor.isClosed()).isTrue();
        Mockito.verify(cursorProvider).open(Mockito.any(Path.class), Mockito.eq(ROOT_KEY));
    }

    private void assertPersistedRows() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_file_inventory", Long.class))
                .isEqualTo(FILE_COUNT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_proposal", Long.class))
                .isEqualTo(EXPECTED_PROPOSAL_COUNT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_issue", Long.class))
                .isEqualTo(EXPECTED_ISSUE_COUNT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_inventory_stage", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_inventory_diff_stage", Long.class))
                .isZero();
    }

    private void logResult(ScanRunEntity run, long durationMillis) {
        double throughput = (run.scannedFileCount() * 1_000.0) / Math.max(1L, durationMillis);
        LOGGER.info(
                "Scan-core benchmark hoàn tất: status={}, files={}, proposals={}, issues={}, durationMs={}, throughputFilesPerSecond={}",
                run.status(),
                run.scannedFileCount(),
                run.proposalCount(),
                run.issueCount(),
                durationMillis,
                Math.round(throughput));
    }

    private void resetTables() {
        jdbcTemplate.update("DELETE FROM scan_outbox_event");
        jdbcTemplate.update("DELETE FROM scan_decision");
        jdbcTemplate.update("DELETE FROM scan_proposal");
        jdbcTemplate.update("DELETE FROM scan_issue");
        jdbcTemplate.update("DELETE FROM scan_file_inventory");
        jdbcTemplate.update("DELETE FROM scan_inventory_diff_stage");
        jdbcTemplate.update("DELETE FROM scan_inventory_stage");
        runs.deleteAllInBatch();
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static Path createEmptyRoot() {
        try {
            return Files.createTempDirectory("scan-core-benchmark-root");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
