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
├── 📦 preview/                               <-- Scan preview/reconciliation benchmark
│   ├── ScanCorePipelineBenchmarkTest.java    <-- Core pipeline
│   ├── SetBasedReconciliationWriteBenchmarkTest.java <-- DB persistence
│   └── legacy/                               <-- Legacy riêng của preview/reconciliation
│       └── JdbcBatchReconciliationWriteBenchmarkTest.java
│
├── 📦 approval/                              <-- Scan approval decision/outbox benchmark
│   └── legacy/                               <-- Legacy riêng của approval
│       └── LegacyScanDecisionBatchBenchmarkIT.java
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
- **Reset benchmark PostgreSQL**: Với fixture chạy trên Testcontainer riêng, reset toàn bộ dataset bằng một lệnh `TRUNCATE TABLE ... CASCADE`, không dùng nhiều lệnh `DELETE FROM <table>` không có `WHERE`. Chỉ dùng `DELETE ... WHERE` cho reset theo scope hoặc các A/B variant cần giữ lại run/staging.
- **Java SDK**: Sử dụng đúng JDK 25 Corretto của dự án (`$env:JAVA_HOME = "$HOME\.jdks\corretto-25.0.4"`).
- **Định dạng Code**: Luôn chạy `mvn spotless:apply` sau khi viết hoặc sửa code test Java.
- **Phân loại Test**: Gắn tag `@Tag("benchmark")` cho các test nặng cần kích hoạt thủ công, tránh làm chậm CI/CD build mặc định.

### 🐘 Nguyên tắc 6: Testcontainers PostgreSQL API hiện hành

- Dùng `org.testcontainers.postgresql.PostgreSQLContainer` từ artifact `testcontainers-postgresql`.
- Theo pattern hiện hành của `ScanCorePipelineBenchmarkTest`:

  ```java
  import org.testcontainers.postgresql.PostgreSQLContainer;

  @Container
  static final PostgreSQLContainer POSTGRES =
          new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));
  ```

- Không dùng `org.testcontainers.containers.PostgreSQLContainer`; class này đã deprecated trong Testcontainers version của dự án.

### 🛡️ Nguyên tắc 7: An toàn Maven CLI trên PowerShell

- Khi gọi Maven từ PowerShell, **luôn quote toàn bộ argument bắt đầu bằng `-D`**. PowerShell nội suy ký tự `$` trong argument không được quote; ví dụ `-Dsurefire.failIfNoSpecifiedTests=false` có thể bị biến thành lifecycle phase `.failIfNoSpecifiedTests=false` trước khi tới Maven.
- Dùng dạng an toàn:

  ```powershell
  .\mvnw.cmd -pl apps/scan-service -am '-Dtest=ScanRunDecisionBatchTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
  ```

- Với command dài hoặc có nhiều property, gom arguments vào array rồi splat để tránh shell parsing:

  ```powershell
  $mavenArgs = @('-pl', 'apps/scan-service', '-am', '-Dtest=ScanRunDecisionBatchTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
  & .\mvnw.cmd @mavenArgs
  ```

- Không dùng backslash để escape `$` trong PowerShell. Nếu Maven báo `Unknown lifecycle phase` chứa phần sau `$`, dừng và chạy lại bằng quoted arguments/array; không kết luận đó là lỗi test.
- Trước khi chạy, đặt `$env:JAVA_HOME` về Corretto 25 và kiểm tra output Maven có `release 25`.

---

### 🏗️ Nguyên tắc 8: Benchmark phải dùng Spring Beans & Schedulers thật của Service (Cấm tự viết lại runner)

- **Độ trung thực cao nhất (Production Fidelity)**:
  - Bài đo Benchmark / Pipeline Test phải đo lường trực tiếp trên **hệ thống Spring Beans thật** của service (`@SpringBootTest`, production workers, schedulers, coordinators, repositories).
  - ❌ **Cấm tự viết lại logic runner**: Không tự tạo mock coordinator, không tự viết custom thread loop thay thế worker, không tự dựng executor giả lập nếu service đã có sẵn background worker / scheduler chính thức (ví dụ: `ApprovalOperationWorker`, `ScanOutboxLaneRelayScheduler`, `CatalogOperationFinalizer`).
- **Kích hoạt Workers & Schedulers qua Properties**:
  - Bật cờ tương ứng của service trong `@SpringBootTest(properties = {...})` (ví dụ: `scan.approval-operation.enabled=true`, `scan.outbox.lane-relay-enabled=true`, `catalog.operation.finalizer-enabled=true`).
  - Cấu hình delay ngắn (`fixed-delay-ms=1` hoặc `scheduler-delay-ms=1`) để scheduler tự động trigger liên tục không có khoảng chết.
- **Chỉ mock boundary hạ tầng ngoại biên**:
  - Chỉ mock điểm tiếp giáp ngoài cùng (Kafka Broker transport, External Network Client) bằng `@TestConfiguration` / `@Primary OutboxMessagePublisher` dạng `immediate acknowledgement` để cô lập data plane nội bộ của service.
- **Pattern chuẩn cho Benchmark Method**:
  1. **Seed data**: Gọi fixture tạo dữ liệu đầu vào.
  2. **Trigger entrypoint thật**: Bấm nút / gọi service thật (ví dụ `operations.accept(runId)`).
  3. **Await durable completion**: Lặp kiểm tra điều kiện kết thúc từ database / durable status (ví dụ `operations.status(id) == "APPROVAL_COMMITTED"` và `outboxEvents.countByPublishedAtIsNull() == 0`) trong khi các Spring Beans thật tự động xử lý ngầm.
  4. **Assert & Log throughput**: Kiểm tra toàn vẹn dữ liệu và ghi log throughput ra console.

---

## 2. Checklist Tự kiểm tra trước khi Bàn giao Test

- [ ] Tên class có hậu tố `Test` hoặc `IT` khớp regex chưa?
- [ ] Dữ liệu mock có sạch sẽ, chuẩn mực không?
- [ ] Dữ liệu mock đã được chuyển vào class `fixture/` dùng chung chưa?
- [ ] Benchmark có dùng trực tiếp Spring Beans / Workers / Schedulers thật của service thay vì tự viết lại runner không?
- [ ] Test có thể chạy độc lập không phụ thuộc dữ liệu cũ trong DB không?
- [ ] Benchmark PostgreSQL có dùng `org.testcontainers.postgresql.PostgreSQLContainer` và reset bằng `TRUNCATE ... CASCADE` đúng scope chưa?
- [ ] Đã chạy `mvn spotless:apply` để định dạng chuẩn chưa?
- [ ] Đã chạy thử qua Maven và xác nhận `BUILD SUCCESS` chưa?
