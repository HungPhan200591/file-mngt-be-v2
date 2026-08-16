# 🚀 Benchmark Chi tiết: Scan Core End-to-End Pipeline

## Baseline trước khi áp dụng `LEFT JOIN`

- **Thời điểm chạy**: `2026-08-16 23:23–23:27 +07:00`
- **Workload**: 1.000.000 synthetic items
- **Phạm vi**: Scan Service pipeline, loại filesystem và Catalog I/O
- **Mutation query**: `INSERT_NEW_SQL` dùng `NOT EXISTS`
- **Kết luận**: baseline **không đạt** vì `FULL_CHANGE` bị PostgreSQL hủy do `statement_timeout`.

| Scenario | Status | Proposals | Issues | Duration (ms) | Throughput (files/s) |
| :--- | :--- | ---: | ---: | ---: | ---: |
| `COLD` | `COMPLETED` | 990.000 | 10.000 | 32.426 | 30.839 |
| `UNCHANGED` | `COMPLETED` | 0 | 0 | 9.522 | 105.020 |
| `INCREMENTAL` | `COMPLETED` | 0 | 1.000 | 11.005 | 90.868 |
| `FULL_CHANGE` | `FAILED` | 792.000 | 8.000 | 153.237 | 6.526 |

`FULL_CHANGE` lỗi tại bước insert inventory với `ERROR: canceling statement due to statement timeout` sau khi xử lý phần lớn chunk. Vì vậy run này chỉ được dùng làm **baseline failure evidence**, không dùng làm SLO thành công.

## Candidate sau khi áp dụng `LEFT JOIN`

- **Thời điểm chạy**: `2026-08-16 23:42 +07:00`
- **Mutation query**: `INSERT_NEW_SQL` dùng `LEFT JOIN scan_file_inventory ... IS NULL`
- **Kết luận tạm thời**: `FULL_CHANGE` đã hoàn thành, nhưng `REVIVED` vẫn bị `statement_timeout` tại cùng bước insert. Candidate chưa đủ bằng chứng để thay thế hoàn toàn baseline.

| Scenario | Status | Proposals | Issues | Duration (ms) | Throughput (files/s) |
| :--- | :--- | ---: | ---: | ---: | ---: |
| `COLD` | `COMPLETED` | 990.000 | 10.000 | 36.747 | 27.213 |
| `UNCHANGED` | `COMPLETED` | 0 | 0 | 9.923 | 100.776 |
| `INCREMENTAL` | `COMPLETED` | 0 | 1.000 | 10.722 | 93.266 |
| `FULL_CHANGE` | `COMPLETED` | 990.000 | 10.000 | 146.197 | 6.840 |
| `REVIVED` | `FAILED` | 0 | 0 | 85.434 | 11.705 |

`REVIVED` bị PostgreSQL hủy câu `INSERT_NEW_SQL` sau khi áp dụng `LEFT JOIN`. Do đó không được coi candidate đã đạt SLO 1M cho toàn bộ workload; cần lấy `EXPLAIN (ANALYZE, BUFFERS)` của câu insert và kiểm tra kế hoạch/index trước khi tiếp tục tối ưu.

## Diagnostic execution plan

Đã thêm `InventoryInsertQueryPlanBenchmarkTest` để A/B chính câu `INSERT_NEW_SQL` hiện tại với candidate `INSERT ... ON CONFLICT (root_key, source_relative_path) DO NOTHING` trong workload `REVIVED`, dùng `EXPLAIN (ANALYZE, BUFFERS, WAL, FORMAT TEXT)`. Mỗi câu lệnh chạy trong transaction riêng và rollback sau khi đo; test không giữ lại các row được `INSERT` bởi `EXPLAIN ANALYZE`.

Chạy riêng test này trong IntelliJ và lọc log theo `Inventory insert plan:`; trường `candidate` có giá trị `left-join` hoặc `on-conflict`. Có thể giảm tải khi kiểm tra nhanh bằng VM option `-Dbenchmark.inventory-insert.rows=100000`; khi lấy bằng chứng 1M, bỏ option này hoặc đặt `-Dbenchmark.inventory-insert.rows=1000000`.

Test `comparesChunkPlanModes` bổ sung A/B `plan_cache_mode=auto` và `plan_cache_mode=force_custom_plan` trên 10 chunk x 100.000 rows. Lọc log theo `Inventory chunk plan:` và đối chiếu `mode`, `chunk`, `Seq Scan/Index Scan`, `temp` và `Execution Time`.

### Plan-cache A/B result — 10 chunks x 100k

- Cả `auto` và `force_custom_plan` đều dùng `Seq Scan` trên toàn bộ `scan_file_inventory` (`1.000.000` rows) ở cả 10 chunk.
- Cả hai đều dùng `Bitmap Index Scan` trên `idx_scan_inventory_diff_stage_run_path` cho khoảng 100k rows của diff stage.
- Cả hai đều có temp spill khoảng `4.000` blocks mỗi chunk.
- `force_custom_plan` chỉ nhanh hơn nhẹ, khoảng `383–430ms/chunk`; `auto` khoảng `425–486ms/chunk` trong run này.

Kết luận: generic plan không phải nguyên nhân chính và chưa có cơ sở áp dụng `SET LOCAL plan_cache_mode = 'force_custom_plan'` vào production. Bottleneck đã được xác nhận là anti-join phải quét/hash toàn bộ inventory lặp lại theo từng chunk. Hướng tiếp theo là materialize tập `new inventory rows` một lần ở cấp run hoặc tách một lần insert-new khỏi vòng commit chunk; không tiếp tục tối ưu bằng timeout hay plan-cache hint.

### Evidence `REVIVED` — 1M rows

- `Hash Anti Join`: khoảng `1.040s` cho node join, tổng execution `1.122s` trong test cô lập.
- `scan_inventory_diff_stage`: `Seq Scan`, đọc `1.000.000` rows.
- `scan_file_inventory`: `Seq Scan`, đọc `1.000.000` rows.
- Hash table: `Batches: 16`, `Memory Usage: 5268kB`.
- Temporary I/O: `temp read=15359`, `temp written=15359` ở node join.
- Không có row mới được insert (`rows=0`).

Kết luận: `LEFT JOIN` đã đổi hình dạng query thành hash anti-join nhưng vẫn phải quét và hash toàn bộ inventory cho mỗi chunk. Đây là nguyên nhân hợp lý khiến full pipeline có thể timeout dù test cô lập chỉ mất khoảng 1,1 giây. Index unique `(root_key, source_relative_path)` tồn tại nhưng planner không chọn index lookup vì workload trả về gần như toàn bộ 1M rows.

Candidate `INSERT ... ON CONFLICT (root_key, source_relative_path) DO NOTHING` đã được benchmark riêng và bị loại bỏ; chưa tăng timeout và chưa dùng candidate này trong production.

### A/B result — `REVIVED`, 1M rows

| Candidate | Execution time | Tuples inserted | Conflicting tuples | Buffer/temp evidence | Decision |
| :--- | ---: | ---: | ---: | :--- | :--- |
| `LEFT JOIN ... IS NULL` | 1.054s | 0 | — | Hash spill, temp I/O | Giữ làm baseline candidate |
| `ON CONFLICT DO NOTHING` | 10.656s | 0 | 1.000.000 | 3.976.398 shared hits, 11.766 buffers written | Loại bỏ |

`ON CONFLICT` phải thực hiện conflict check qua unique arbiter index cho toàn bộ 1M rows dù không insert row nào, nên chậm hơn khoảng 10,1 lần trong workload `REVIVED`. Candidate này không được áp dụng vào production.

Các dòng `Mockito is currently self-attaching` và cảnh báo Byte Buddy chỉ là warning về dynamic Java agent trên JDK 25; benchmark vẫn chạy và không phải nguyên nhân của chênh lệch hiệu năng.

## Implementation sau khi loại trừ plan-cache và `ON CONFLICT`

Warm reconciliation hiện materialize `is_new` một lần trong `scan_inventory_diff_stage` bằng `LEFT JOIN`. Mỗi chunk giữ nguyên transaction boundary nhưng chỉ `UPDATE` các row `is_new = false` và `INSERT` các row `is_new = true`; không còn anti-join lại toàn bộ `scan_file_inventory` trong từng chunk.

Migration/runtime correctness và full-pipeline benchmark sau thay đổi này vẫn là gate VERIFY PENDING.

- **Mã bài đo**: `BENCH-03-SCAN-CORE`
- **Class thực thi**: [`ScanCorePipelineBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/pipeline/ScanCorePipelineBenchmarkTest.java)
- **Workload**: 1.000.000 files (990.000 proposals, 10.000 issues, 10 chunks x 100k items)
- **Môi trường**: PostgreSQL Testcontainers (`postgres:18.0-alpine`), Java 25, In-Memory Stream Cursor (Zero Disk I/O)

---

## 1. Bảng Phân Tích Chi Tiết Từng Khâu (Total = 37,960 ms)

| Khâu thực thi | Chi tiết tác vụ | Thời gian (ms) | % Thời gian | Tốc độ tương đương |
| :--- | :--- | :---: | :---: | :---: |
| **1. Ghi Proposals vào DB** | PostgreSQL Binary `COPY scan_proposal` (990.000 dòng) | **8,791 ms** | **23.2%** | ~112,600 proposals/s |
| **2. Phân tích Regex & Đọc Keyset** | Keyset DB Read + 8 Virtual Threads parse 1M files | **~8,500 ms** | **22.4%** | ~117,600 files/s |
| **3. Ghi Inventory vào DB** | Set-based SQL `INSERT INTO scan_file_inventory` (1M dòng) | **6,647 ms** | **17.5%** | ~150,400 rows/s |
| **4. Materialize Diff SQL** | PostgreSQL Set-based Diff giữa Staging & Inventory | **~6,500 ms** | **17.1%** | — |
| **5. Discovery Staging** | Đọc In-Memory stream $\to$ COPY `scan_inventory_stage` | **~6,000 ms** | **15.8%** | ~166,000 files/s |
| **6. Finalize & Checkpoint** | Ghi Issue (77ms), Commit (21ms), Dọn dẹp staging | **~1,522 ms** | **4.0%** | — |
| **TỔNG CỘNG** | **Xử lý toàn diện 1.000.000 Files** | **37,960 ms** | **100%** | **26,343 files/s** |

---

## 2. Telemetry Timeline Snapshot
```text
[runId=01a009ff-5c0c-7bb4-86ec-3f0306e00a38] scan.execution.terminal: 
  phase=completed, durationMs=37960, files=1000000, proposals=990000, issues=10000, 
  committedChunks=10, rolledBackChunks=0, 
  inventoryWriteMs=6647, proposalCopyMs=8791, issueCopyMs=77, checkpointMs=6, commitMs=21, 
  chunkTransactionMs=15576
```

---

## 3. Đánh Giá Điểm Nghẽn & Khả Năng Tối Ưu
- **Tầng Database Persistence (Ghi Proposal + Inventory)** chiếm **40.7% (15.44s)**: Là chi phí I/O lớn nhất khi commit an toàn gần 2 triệu bản ghi.
- **Tầng Discovery & Diff Staging** chiếm **32.9% (12.50s)**: Tốn chi phí ghi/đọc qua 2 bảng tạm trung gian.
- **Tầng CPU Regex Parsing** chiếm **22.4% (8.50s)**: 8 Virtual Threads phân tích 1 triệu file Regex với tốc độ >117.000 files/s.
