# SC-01 — Scan một triệu filesystem entry

Scenario, prerequisite, workload contract và evidence cần thu thập do [roadmap](../../../ADVANCED_MICROSERVICES_STUDY_ROADMAP.md) sở hữu. Trạng thái study: đang nghiên cứu; chưa có benchmark chứng minh V2 đạt quy mô này.

## Study pack

| Artifact | Vị trí | Trạng thái |
| --- | --- | --- |
| Deep-dive | [01-deep-dive.md](./01-deep-dive.md) | Đã tạo |
| Chi tiết Luồng & Điểm chạm | [02-architecture-touchpoints-and-flows.md](./02-architecture-touchpoints-and-flows.md) | Đã tạo |
| Giải pháp Lọc trùng Xuyên Service | [03-cross-service-deduplication.md](./03-cross-service-deduplication.md) | Đã tạo |
| Break task triển khai | [04-break-task.md](./04-break-task.md) | Đã tạo |
| Phương án UI/UX Solution Behavior | [05-ui-ux-solution-behavior.md](./05-ui-ux-solution-behavior.md) | Đã tạo |
| Java 25 Fixture Tool | [fixture-tools](../../../../tests/fixtures/tools/pom.xml) (package `com.filemngt.tools.sc01_scan_one_million`) | Đã tạo |
| Summary | `summary/` | Chưa yêu cầu |
| Question chain | [question-bank/01-question-chain.md](./question-bank/01-question-chain.md) | Đã tạo, bổ sung dần theo BT/deep-dive |

Xem [UC-01](../../core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) cho flow nhỏ làm prerequisite. Không thay architecture, contract, owner context hoặc `docs/STATUS.md`.

Chạy nhanh qua NPM scripts từ root repository:

```bash
# Sinh 1 triệu file fixture rỗng cho SC-01
npm run fixture:sc01:gen

# Dọn dẹp / xóa sạch 1 triệu file fixture SC-01
npm run fixture:sc01:clean

# Xem trợ giúp lệnh fixture
npm run help:fixture
```

## Benchmark riêng chi phí đọc filesystem

Lệnh sau chỉ duyệt cây và đọc metadata theo đúng access pattern hiện tại của
`ScanExecutor`: `Files.walk` → regular file → non-symlink → `Files.size` →
`Files.getLastModifiedTime`. Benchmark không đọc nội dung file, không ghi dữ liệu
và không truy cập database.

```bash
npm run fixture:sc01:benchmark-read
```

Có thể chỉ định fixture root khác từ thư mục gốc repository:

```powershell
java '-Dfile.encoding=UTF-8' '-DtargetDir=D:/path/to/fixture' tests/fixtures/tools/src/main/java/com/filemngt/tools/sc01_scan_one_million/BenchmarkFilesystemRead.java
```

Đo phương án `walkFileTree` tái sử dụng `BasicFileAttributes` và stream toàn bộ
fixture qua một phiên PostgreSQL `COPY FROM STDIN`:

```bash
npm run fixture:sc01:benchmark-copy
```

Benchmark tạo `TEMP TABLE` có index tương đương staging trong một transaction,
đếm số row rồi `ROLLBACK`; không ghi vào `scan_inventory_stage` hoặc bảng dữ
liệu thật. Kết quả mặc định bao gồm filesystem walk, encode text, IPC và indexed
COPY. Dùng `-DwithIndex=false` khi cần đo raw COPY không có index.

Evidence local ngày 2026-08-07 với một triệu fixture file rỗng, NTFS cache warm,
PostgreSQL local và staging index bật:

- Phát đủ dữ liệu từ `walkFileTree` dùng `BasicFileAttributes`: 2,390 giây.
- `walk + encode + IPC + indexed COPY`: 2,890 giây, khoảng 346.014 file/giây.
- Tổng gồm connect, tạo TEMP table/index, đếm row và rollback: 3,086 giây.

Đây là microbenchmark discovery/COPY, chưa gồm set-based inventory diff,
proposal/issue, lease heartbeat hay finalization nên không phải latency cam kết
của toàn scan run.

Hoặc chạy trực tiếp CLI / Maven compile:

```text
mvn -f tests/fixtures/tools/pom.xml compile
java -cp tests/fixtures/tools/target/classes -Dconcurrency=32 com.filemngt.tools.sc01_scan_one_million.GenerateOneMillionJokeVideoFixtures
java -cp tests/fixtures/tools/target/classes com.filemngt.tools.sc01_scan_one_million.CleanOneMillionJokeVideoFixtures
```
