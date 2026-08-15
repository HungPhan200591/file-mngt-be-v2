# SC-01 — Fixture và microbenchmark commands

> Tài liệu vận hành study. Chỉ đọc khi task yêu cầu tạo fixture hoặc đo filesystem/COPY; không phải
> context mặc định và không phải SLO contract.

## Fixture commands

Chạy từ thư mục gốc repository:

```bash
# Sinh 1 triệu file fixture rỗng cho SC-01
npm run fixture:sc01:gen

# Dọn dẹp / xóa sạch 1 triệu file fixture SC-01
npm run fixture:sc01:clean

# Xem trợ giúp lệnh fixture
npm run help:fixture
```

## Filesystem read benchmark

Benchmark chỉ duyệt cây và đọc metadata theo access pattern hiện tại của `ScanExecutor`: `Files.walk`
→ regular file → non-symlink → `Files.size` → `Files.getLastModifiedTime`. Không đọc nội dung file,
không ghi dữ liệu và không truy cập database.

```bash
npm run fixture:sc01:benchmark-read
```

Có thể chỉ định fixture root khác:

```powershell
java '-Dfile.encoding=UTF-8' '-DtargetDir=D:/path/to/fixture' tests/fixtures/tools/src/main/java/com/filemngt/tools/sc01_scan_one_million/BenchmarkFilesystemRead.java
```

## Filesystem + PostgreSQL COPY benchmark

Đo `walkFileTree` tái sử dụng `BasicFileAttributes` và stream fixture qua một phiên PostgreSQL
`COPY FROM STDIN`:

```bash
npm run fixture:sc01:benchmark-copy
```

Benchmark tạo `TEMP TABLE` có index tương đương staging, đếm row rồi `ROLLBACK`; không ghi vào
`scan_inventory_stage` hoặc bảng dữ liệu thật. Kết quả mặc định gồm filesystem walk, encode text,
IPC và indexed COPY. Dùng `-DwithIndex=false` để đo raw COPY không có index.

Evidence local ngày 2026-08-07 với fixture 1M, NTFS cache warm, PostgreSQL local và staging index:

- `walkFileTree` với `BasicFileAttributes`: 2,390 giây.
- `walk + encode + IPC + indexed COPY`: 2,890 giây, khoảng 346.014 file/giây.
- Tổng gồm connect, TEMP table/index, đếm row và rollback: 3,086 giây.

Đây là microbenchmark discovery/COPY, chưa gồm inventory diff, proposal/issue, lease heartbeat hoặc
finalization; không dùng làm latency cam kết của toàn scan run.

## Fixture tool CLI

```text
mvn -f tests/fixtures/tools/pom.xml compile
java -cp tests/fixtures/tools/target/classes -Dconcurrency=32 com.filemngt.tools.sc01_scan_one_million.GenerateOneMillionJokeVideoFixtures
java -cp tests/fixtures/tools/target/classes com.filemngt.tools.sc01_scan_one_million.CleanOneMillionJokeVideoFixtures
```
