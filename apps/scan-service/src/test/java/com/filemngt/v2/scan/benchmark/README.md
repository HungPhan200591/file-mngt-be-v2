# 🧪 Scan Service Benchmark Suite

Thư mục này tổ chức toàn bộ các bài kiểm thử hiệu năng độc lập (**Benchmark Suite**) của `scan-service` cho các tải trọng cực lớn (**1.000.000+ files / Workload SC-01**).

---

## 1. Cấu trúc Phân tầng Package (Modular Benchmark)

```text
com.filemngt.v2.scan.benchmark/
├── fixture/                               <-- Cung cấp mock data & context fixtures
│   └── SyntheticScanItemGenerator.java    <-- Generator sinh dữ liệu sạch (10k -> 1.000.000+ files)
│
├── pipeline/                              <-- Các bài benchmark cho Scan Pipeline (Phase 1 -> 6)
│   ├── ScanParallelAnalyzerBenchmarkTest.java <-- [PHASE 4] CPU Regex, Evidence JSON & Virtual Threads trong RAM
│   └── SetBasedReconciliationWriteBenchmarkTest.java <-- [PHASE 3 & 5] PostgreSQL Direct COPY & SQL Set-based trên DB
│
├── legacy/                                <-- Các bài benchmark đối chiếu kiến trúc cũ (Baseline)
│   └── JdbcBatchReconciliationWriteBenchmarkTest.java <-- [LEGACY] Baseline JDBC Batch truyền thống (84s / 1M)
│
├── results/                               <-- Báo cáo đo đạc chi tiết của từng bài benchmark
│   ├── 01-legacy-jdbc-batch-baseline.md
│   ├── 02-database-set-based-persistence.md
│   └── 03-phase4-parallel-analyzer-cpu.md
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

### ⚡ 1. Đo Phase 4 (CPU Java Virtual Threads trong RAM - 1M files):
```powershell
$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test -pl apps/scan-service -Dtest=ScanParallelAnalyzerBenchmarkTest
```

### 💾 2. Đo Phase 3 & 5 (Database Direct COPY + SQL Set-based - 1M rows):
```powershell
$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test -pl apps/scan-service -Dtest=SetBasedReconciliationWriteBenchmarkTest
```

### 📜 3. Đo Baseline JDBC Batch truyền thống (Đối chiếu lịch sử):
```powershell
$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test -pl apps/scan-service -Dtest=JdbcBatchReconciliationWriteBenchmarkTest
```

