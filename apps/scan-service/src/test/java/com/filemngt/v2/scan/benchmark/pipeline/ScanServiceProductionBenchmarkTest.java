package com.filemngt.v2.scan.benchmark.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogExistenceClient;
import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.filesystem.ScanFileCursor;
import com.filemngt.v2.scan.adapter.out.filesystem.ScanFileCursorProvider;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import com.filemngt.v2.scan.application.scan.ScanService;
import com.filemngt.v2.scan.benchmark.fixture.SyntheticScanItemGenerator;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
 * Benchmark toàn diện Service Production: Kích hoạt trực tiếp qua {@link ScanService#start(String, boolean)}.
 *
 * Mock đầu vào Filesystem bằng Stream In-Memory (Zero disk I/O) và chạy 100% logic Service PRD.
 *
 * Lệnh chạy:
 * {@code mvn test -pl apps/scan-service -Dtest=ScanServiceProductionBenchmarkTest}
 */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(properties = "scan.outbox.enabled=false")
class ScanServiceProductionBenchmarkTest {

    private static final int BENCHMARK_FILES_COUNT = 1000_000;
    private static final Path EMPTY_BENCHMARK_ROOT = createEmptyRoot();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.0-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("scan.roots[0].key", () -> "benchmark-root");
        registry.add("scan.roots[0].path", EMPTY_BENCHMARK_ROOT::toString);
        registry.add("scan.roots[0].profile", () -> "JOKE_VIDEO");
    }

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

    @BeforeEach
    void setUp() {
        // 1. Mock Catalog Snapshot
        Mockito.when(catalogClient.fetch(Mockito.anyString()))
                .thenReturn(Optional.of(new ScanRegistrySnapshot(100L, "JOKE_VIDEO", List.of("CODE-001"), List.of())));

        // 2. Mock Catalog Existence check trả về NEW
        Mockito.when(catalogExistenceClient.classify(Mockito.any(), Mockito.anyList()))
                .thenAnswer(invocation -> {
                    List<CatalogExistenceClient.Candidate> candidates = invocation.getArgument(1);
                    Map<UUID, CatalogExistenceClient.Result> map = new HashMap<>();
                    for (var c : candidates) {
                        map.put(
                                c.clientRef(),
                                new CatalogExistenceClient.Result(
                                        c.clientRef(),
                                        CatalogExistenceClient.Classification.NEW_SUBJECT,
                                        null,
                                        null,
                                        null));
                    }
                    return map;
                });

        // 3. Mock đầu vào Stream In-Memory (Zero disk I/O, nạp thẳng 100k items từ RAM)
        List<ScanInventoryItem> items =
                SyntheticScanItemGenerator.generateItems("benchmark-root", BENCHMARK_FILES_COUNT);
        Mockito.when(cursorProvider.open(Mockito.any(), Mockito.any()))
                .thenAnswer(_ -> new InMemoryScanFileCursor(items));

        // 4. Dọn dẹp sạch DB trước mỗi lần chạy
        jdbcTemplate.update("DELETE FROM scan_outbox_event");
        jdbcTemplate.update("DELETE FROM scan_decision");
        jdbcTemplate.update("DELETE FROM scan_proposal");
        jdbcTemplate.update("DELETE FROM scan_issue");
        jdbcTemplate.update("DELETE FROM scan_file_inventory");
        jdbcTemplate.update("DELETE FROM scan_inventory_diff_stage");
        jdbcTemplate.update("DELETE FROM scan_inventory_stage");
        runs.deleteAllInBatch();
    }

    @Test
    @DisplayName("Benchmark Service PRD: Gọi scanService.start() và đo thời gian xử lý thực tế")
    void benchmark_RealProductionScanService() throws Exception {
        System.out.printf(
                ">>> [Benchmark] Đang gọi scanService.start(\"benchmark-root\", false) trên %,d files (In-Memory Input Stream)...%n",
                BENCHMARK_FILES_COUNT);
        long startTime = System.currentTimeMillis();

        // GỌI ĐÚNG 1 DÒNG METHOD ENTRYPOINT CỦA SERVICE PRD
        ScanRunView view = scanService.start("benchmark-root", false);

        // Chờ Service PRD hoàn tất tác vụ nền (Polled theo status trong DB)
        ScanRunEntity run;
        while (true) {
            run = runs.findById(view.id()).orElseThrow();
            if (run.status() == ScanRunStatus.COMPLETED || run.status() == ScanRunStatus.FAILED) {
                break;
            }
            Thread.sleep(50);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        double durationSeconds = durationMs / 1000.0;
        double throughput = (run.scannedFileCount() * 1000.0) / Math.max(1, durationMs);

        System.out.println(
                "\n==========================================================================================");
        System.out.println(
                "           KẾT QUẢ ĐO ĐẠC TOÀN DIỆN SERVICE PRODUCTION (SCANSERVICE.START)                ");
        System.out.println(
                "==========================================================================================");
        System.out.printf("  - Trạng thái hoàn thành          : %s%n", run.status());
        System.out.printf("  - Tổng số file quét thành công   : %,d files%n", run.scannedFileCount());
        System.out.printf("  - Proposals sinh ra trong DB     : %,d proposals%n", run.proposalCount());
        System.out.printf("  - Issues phát hiện trong DB      : %,d issues%n", run.issueCount());
        System.out.printf("  - Tổng thời gian thực thi        : %,d ms (%.3f giây)%n", durationMs, durationSeconds);
        System.out.printf("  - Tốc độ xử lý thực tế           : %,.0f files/giây%n", throughput);
        System.out.println(
                "==========================================================================================\n");

        assertThat(run.status()).isEqualTo(ScanRunStatus.COMPLETED);
        assertThat(run.scannedFileCount()).isEqualTo(BENCHMARK_FILES_COUNT);
    }

    private static Path createEmptyRoot() {
        try {
            return Files.createTempDirectory("scan-empty-root");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class InMemoryScanFileCursor implements ScanFileCursor {
        private final Iterator<ScanInventoryItem> iterator;

        InMemoryScanFileCursor(List<ScanInventoryItem> items) {
            this.iterator = items.iterator();
        }

        @Override
        public ScanInventoryItem next() {
            return iterator.hasNext() ? iterator.next() : null;
        }

        @Override
        public void close() {}
    }
}
