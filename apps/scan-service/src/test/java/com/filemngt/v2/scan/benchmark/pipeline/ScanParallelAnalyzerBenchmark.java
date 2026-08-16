package com.filemngt.v2.scan.benchmark.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.application.scan.ScanChunk;
import com.filemngt.v2.scan.application.scan.ScanExecutionContext;
import com.filemngt.v2.scan.application.scan.ScanFileAnalyzer;
import com.filemngt.v2.scan.application.scan.ScanParallelAnalyzer;
import com.filemngt.v2.scan.benchmark.fixture.SyntheticScanItemGenerator;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Benchmark Phase 4 (Parallel Analyzer) trên 1.000.000 records giả lập trong RAM.
 *
 * Chạy từ root:
 * {@code mvn test -pl apps/scan-service -Dtest=ScanParallelAnalyzerBenchmark}
 */
@Tag("benchmark")
class ScanParallelAnalyzerBenchmark {

    private ScanParallelAnalyzer parallelAnalyzer;
    private ScanExecutionContext context;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper();
        var evidenceCodec = new ScanEvidenceCodec(objectMapper);
        var fileAnalyzer = new ScanFileAnalyzer(evidenceCodec);
        this.parallelAnalyzer = new ScanParallelAnalyzer(fileAnalyzer);

        var root = SyntheticScanItemGenerator.createDefaultVideoRoot();
        var snapshot = SyntheticScanItemGenerator.createDefaultRegistrySnapshot();

        this.context = new ScanExecutionContext(
                UUID.fromString("01912a3b-4c5d-7e8f-9a0b-123456789abc"),
                SyntheticScanItemGenerator.DEFAULT_WORKER_ID,
                root,
                snapshot,
                false);
    }

    @Test
    @DisplayName("Benchmark Phase 4: Phân tích 1.000.000 files trên 8 Virtual Threads")
    void benchmark_Analyze_OneMillionFiles() {
        int totalFiles = 1_000_000;
        int parallelism = 8;

        System.out.println(">>> [Benchmark] Đang sinh 1.000.000 files bằng SyntheticScanItemGenerator...");
        List<ScanInventoryItem> items = SyntheticScanItemGenerator.generateItems(totalFiles);
        assertThat(items).hasSize(totalFiles);

        // Khởi động JIT compiler (Warmup 10.000 files)
        System.out.println(">>> [Benchmark] Khởi động JVM Warmup 10.000 files...");
        parallelAnalyzer.analyzeParallel(context, items.subList(0, 10_000), parallelism);

        System.out.println(">>> [Benchmark] BẮT ĐẦU ĐO ĐẠC 1.000.000 FILES TRÊN 8 VIRTUAL THREADS...");
        System.gc();

        long startNanos = System.nanoTime();
        ScanChunk chunk = parallelAnalyzer.analyzeParallel(context, items, parallelism);
        long durationNanos = System.nanoTime() - startNanos;
        long durationMillis = durationNanos / 1_000_000L;

        double durationSeconds = durationMillis / 1000.0;
        double throughput = (totalFiles * 1000.0) / durationMillis;
        double microSecondsPerFile = (double) durationNanos / (totalFiles * 1000.0);

        System.out.println("\n==================================================================");
        System.out.println("          KẾT QUẢ BENCHMARK PHASE 4: JAVA PARALLEL ANALYZER       ");
        System.out.println("==================================================================");
        System.out.printf("  - Tổng số file phân tích     : %,d files%n", totalFiles);
        System.out.printf("  - Số luồng Virtual Threads   : %d partitions%n", parallelism);
        System.out.printf("  - Tổng thời gian thực thi    : %,d ms (%.3f giây)%n", durationMillis, durationSeconds);
        System.out.printf("  - Tốc độ xử lý (Throughput)  : %,.0f files/giây%n", throughput);
        System.out.printf("  - Độ trễ trung bình mỗi file : %.2f micro-giây (µs/file)%n", microSecondsPerFile);
        System.out.printf(
                "  - Hợp lệ (Proposals)         : %,d items%n",
                chunk.proposals().size());
        System.out.printf(
                "  - Lỗi/Mơ hồ (Issues)         : %,d items%n", chunk.issues().size());
        System.out.println("==================================================================\n");

        assertThat(chunk.proposals().size() + chunk.issues().size()).isEqualTo(totalFiles);
    }
}
