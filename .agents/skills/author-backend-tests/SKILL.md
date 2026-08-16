---
name: author-backend-tests
description: "Thực hiện viết, tổ chức và chuẩn hóa Unit Test, Integration Test và Benchmark Test cho Backend V2 (Spring Boot 4 / Java 25). Đảm bảo tuân thủ 5 nguyên tắc vàng: quy chuẩn đặt tên regex (*Test/*IT), dữ liệu mock sạch không nhạy cảm, tách biệt common fixture, cấu trúc phân tầng benchmark và dashboard báo cáo dẫn link chi tiết."
---

# Quy chuẩn Viết và Tổ chức Backend Test & Benchmark (Backend V2)

Skill này dùng khi viết mới, mở rộng hoặc chuẩn hóa **Unit Test**, **Integration Test** và **Benchmark Performance Test** trong Backend V2 (`file-mngt-be-v2`).

---

## 1. Năm Nguyên tắc Vàng bắt buộc

### 🛡️ Nguyên tắc 1: Đặt tên Class khớp chuẩn Regex của Linter / Sonar
Mọi test class nằm trong `src/test/java/` **bắt buộc** phải tuân theo biểu thức chính quy:
`^((Test|IT)[a-zA-Z0-9_]+|[A-Z][a-zA-Z0-9_]*(Test|Tests|TestCase|IT|ITCase))$`

- **Unit Test & Benchmark Test**: Bắt buộc có hậu tố `Test` (vd: `ScanParallelAnalyzerBenchmarkTest`, `UserServiceTest`).
- **Integration Test**: Bắt buộc có hậu tố `IT` hoặc `IntegrationTest` (vd: `ScanIntegrationTest`, `MediaWorkerIT`).
- ❌ **Cấm**: Đặt tên test class không có hậu tố như `ScanParallelAnalyzerBenchmark`, `SetBasedReconciliationWriteBenchmark`.

---

### 🧼 Nguyên tắc 2: Dữ liệu Mock / Synthetic Dataset sạch & chuẩn hóa
- **Tuyệt đối không** sử dụng từ khóa nhạy cảm, dung tục, thô tục hoặc JAV (diễn viên, hãng phim nhạy cảm) trong test data.
- **Sử dụng format chuẩn mực**:
  - Entity/Studio: `Studio_Alpha`, `Studio_Beta`, `Studio_Gamma`
  - Person/Artist: `Artist_Alex`, `Artist_Brian`, `Artist_Chris`
  - Code/Identifier: `CODE-001`, `DOC-001`, `MEDIA-123`
  - Tags: `HD`, `4K`, `OFFICIAL`, `SAMPLE`, `TRAILER`, `REMASTER`
  - Email/User: `user01@example.com`, `admin@example.org`

---

### 🧩 Nguyên tắc 3: Tách riêng Common Fixture / Generator
- **Không copy-paste** logic sinh mock data hoặc khởi tạo context lặp lại trong từng file test.
- Luôn tạo class fixture riêng trong sub-package `fixture/` (vd: `SyntheticScanItemGenerator.java`, `UserTestFixture.java`).
- Class generator phải hỗ trợ:
  1. Phương thức sinh nhanh số lượng lớn (`generateItems(1_000_000)`).
  2. Tùy biến tỷ lệ lỗi/tag (`generateItems(rootKey, count, issueRate, taggedRate)`).
  3. Factory method khởi tạo snapshot/config mặc định.

---

### 📂 Nguyên tắc 4: Cấu trúc Phân tầng Thư mục Benchmark Suite
Khi xây dựng module benchmark hoặc kiểm thử tải trọng lớn (100k -> 1.000.000+ bản ghi), tổ chức cây thư mục theo chuẩn:

```text
com.filemngt.v2.<service>.benchmark/
├── 📦 fixture/                               <-- Cung cấp mock data & context fixtures
│   └── SyntheticScanItemGenerator.java       <-- Generator sinh dữ liệu sạch tái sử dụng
│
├── 📦 pipeline/                              <-- Các bài benchmark kiến trúc hiện tại
│   ├── ScanParallelAnalyzerBenchmarkTest.java <-- [PHASE 4] CPU Analyze trong RAM
│   └── SetBasedReconciliationWriteBenchmarkTest.java <-- [PHASE 3 & 5] DB Persistence
│
├── 📦 legacy/                                <-- Các bài benchmark đối chiếu công nghệ cũ
│   └── JdbcBatchReconciliationWriteBenchmarkTest.java <-- Baseline JDBC cũ (84s / 1M)
│
├── 📂 results/                               <-- Báo cáo chi tiết từng lần đo
│   ├── 01-legacy-jdbc-batch-baseline.md
│   ├── 02-database-set-based-persistence.md
│   └── 03-phase4-parallel-analyzer-cpu.md
│
├── 📊 BENCHMARK_RESULTS.md                   <-- Dashboard tổng hợp Summary Matrix dẫn link
└── 📄 README.md                              <-- Chỉ mục điều hướng & CLI cheat sheet
```

- **`BENCHMARK_RESULTS.md`**: Chỉ lưu **Summary Matrix** tóm tắt so sánh giữa các thế hệ; chi tiết từng bài đo phải nằm trong `results/*.md`.

---

### ⚙️ Nguyên tắc 5: Độc lập Môi trường & Clean Formatting
- **Test Isolation**: Dọn dẹp bảng/state trước và sau mỗi test run (`resetTables()`), không phụ thuộc thứ tự chạy giữa các test.
- **Java SDK**: Sử dụng đúng JDK 25 Corretto của dự án (`$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"`).
- **Định dạng Code**: Luôn chạy `mvn spotless:apply` sau khi viết hoặc sửa code test Java.
- **Phân loại Test**: Gắn tag `@Tag("benchmark")` cho các test nặng cần kích hoạt thủ công, tránh làm chậm CI/CD build mặc định.

---

## 2. Checklist Tự kiểm tra trước khi Bàn giao Test

- [ ] Tên class có hậu tố `Test` hoặc `IT` khớp regex chưa?
- [ ] Dữ liệu mock có sạch sẽ, chuẩn mực không?
- [ ] Dữ liệu mock đã được chuyển vào class `fixture/` dùng chung chưa?
- [ ] Test có thể chạy độc lập không phụ thuộc dữ liệu cũ trong DB không?
- [ ] Đã chạy `mvn spotless:apply` để định dạng chuẩn chưa?
- [ ] Đã chạy thử qua Maven và xác nhận `BUILD SUCCESS` chưa?
