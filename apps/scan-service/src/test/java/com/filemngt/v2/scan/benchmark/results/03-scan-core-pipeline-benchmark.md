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
