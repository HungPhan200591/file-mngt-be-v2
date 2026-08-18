# 🧪 Scan Service Benchmark Suite

Thư mục này tổ chức toàn bộ các bài kiểm thử hiệu năng độc lập (**Benchmark Suite**) của `scan-service` cho các tải trọng cực lớn (**1.000.000+ files / Workload SC-01**).

---

## 1. Cấu trúc Phân tầng Package (Modular Benchmark)

```text
com.filemngt.v2.scan.benchmark/
├── fixture/                               <-- Cung cấp mock data & context fixtures
│   ├── SyntheticScanItemGenerator.java    <-- Generator sinh dữ liệu sạch (10k -> 1.000.000+ files)
│   └── InMemoryScanFileCursor.java         <-- Cursor fixture loại filesystem I/O
│
├── preview/                               <-- Scan preview/reconciliation pipeline
│   ├── ScanCorePipelineBenchmarkTest.java <-- Scan core, loại filesystem và Catalog I/O
│   └── SetBasedReconciliationWriteBenchmarkTest.java <-- [PHASE 3 & 5] PostgreSQL Direct COPY & SQL Set-based trên DB
│
│   └── legacy/                            <-- Baseline cũ riêng của preview/reconciliation
│       └── JdbcBatchReconciliationWriteBenchmarkTest.java <-- Legacy JDBC Batch (84s / 1M)
│
├── approval/                              <-- Scan approval decision/outbox
│   └── ApprovalDecisionChunkingBenchmarkTest.java <-- FT-045 bounded chunk candidate
│
├── outbox/                                <-- FT-052 outbox relay
│   └── ScanOutboxWaveBaselineBenchmarkTest.java <-- Legacy wave baseline
│   └── ScanOutboxContinuousDrainBenchmarkTest.java <-- Continuous-drain candidate
│
├── results/                               <-- Báo cáo đo đạc chi tiết của từng bài benchmark
│   ├── 01-legacy-jdbc-batch-baseline.md
│   ├── 02-database-set-based-persistence.md
│   ├── 03-scan-core-pipeline-benchmark.md
│   ├── 04-inventory-diff-query-benchmark.md
│   └── 05-legacy-approval-decision-batch-baseline.md
│   └── 06-ft052-legacy-outbox-wave-baseline.md
│
├── BENCHMARK_RESULTS.md                   <-- Dashboard tổng hợp chỉ số của tất cả các lần đo
└── README.md                              <-- Chỉ mục điều hướng & CLI cheat sheet

```

---

## 2. Common Fixture: `SyntheticScanItemGenerator`

Tất cả các bài benchmark đều dùng chung `fixture.SyntheticScanItemGenerator` để sinh dữ liệu mock chuẩn mực, tránh việc viết lặp lại code sinh data:

```java
import com.filemngt.v2.scan.benchmark.fixture.SyntheticScanItemGenerator;

// 1. Sinh nhanh 1.000.000 bản ghi giả lập sạch sẽ:
List<ScanInventoryItem> items = SyntheticScanItemGenerator.generateItems(1_000_000);

// 2. Tùy biến tỷ lệ file lỗi (issue) và file có tag:
List<ScanInventoryItem> items = SyntheticScanItemGenerator.generateItems("ROOT_VIDEO", 100_000, 0.01, 0.10);

// 3. Lấy Registry Snapshot và Root mặc định:
ScanRegistrySnapshot snapshot = SyntheticScanItemGenerator.createDefaultRegistrySnapshot();
ScanProperties.Root root = SyntheticScanItemGenerator.createDefaultVideoRoot();
```

---

## 3. Danh mục Lệnh chạy từng bài Benchmark

Tất cả các lệnh Maven chạy từ **thư mục gốc dự án** với JDK 25 Corretto:

### 💾 1. Preview: đo Database Set-based Persistence (1M rows trên PostgreSQL):
```powershell
$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test -Pbenchmark -pl apps/scan-service -Dtest=SetBasedReconciliationWriteBenchmarkTest
```

### 🐢 2. Preview legacy: đo JDBC Batching (50k - 1M rows):
```powershell
$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test -Pbenchmark -pl apps/scan-service -Dtest=JdbcBatchReconciliationWriteBenchmarkTest
```

### 🚀 3. Preview: đo Scan Service core pipeline, loại filesystem và Catalog I/O:
```powershell
$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest
```

### ✅ 4. Approval legacy: đo approve-all decision/outbox path:
```powershell
$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test -Pbenchmark -pl apps/scan-service -Dtest=ApprovalDecisionChunkingBenchmarkTest
```

Profile `benchmark` là bắt buộc. `mvn test` mặc định loại toàn bộ test gắn `@Tag("benchmark")` để
không đưa workload 1M records vào test suite/CI thông thường.

### 📤 5. FT-052: đo legacy outbox wave baseline (25k + 1M):

```powershell
$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test -Pbenchmark -pl apps/scan-service -Dtest=ScanOutboxWaveBaselineBenchmarkTest
```

Sau FT-052, thay test bằng `ScanOutboxContinuousDrainBenchmarkTest` và giữ nguyên workload/fixture để đối chiếu.
