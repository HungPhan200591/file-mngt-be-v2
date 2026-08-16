# 📊 Scan Service Benchmark Results Dashboard

Tài liệu này tổng hợp **bảng chỉ số tóm tắt (Executive Summary)** của tất cả các đợt đo đạc hiệu năng trên hệ thống `scan-service` cho tải trọng **1.000.000 bản ghi**. Chi tiết phương pháp, biểu đồ và phân tích chuyên sâu được dẫn link trực tiếp tới từng báo cáo riêng biệt.

---

## 1. Bảng Tổng hợp Chỉ số Toàn cảnh (Summary Matrix)

| Mã bài đo | Tên Hạng mục Đo | Phạm vi / Công nghệ | Workload | Thời gian đo | Tốc độ (Throughput) | Báo cáo Chi tiết |
|---|---|---|:---:|:---:|:---:|:---:|
| **`BENCH-01`** | **Legacy JDBC Batch Baseline** | JDBC Batch 50k / PostgreSQL | 1.000.000 diff | **43.45s – 84.65s** | ~23.000 files/s | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/01-legacy-jdbc-batch-baseline.md) |
| **`BENCH-02`** | **Database Set-based Persistence** | Direct COPY + SQL Set-based | 1.000.000 diff | **18.67s – 19.34s** | ~53.000 rows/s | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/02-database-set-based-persistence.md) |
| **`BENCH-03`** | **Scan Core End-to-End Pipeline** | Full Service (`scanService.start`) | 1.000.000 files | **37.96s** | **26.343 files/s** | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/03-scan-core-pipeline-benchmark.md) |
| **`BENCH-04`** | **Scan Decision & Outbox Chunking** | Keyset 25k chunks / Tx boundary | 1.000.000 decisions | *(Đang triển khai)* | *(Pending FT-045)* | *(Sắp bổ sung)* |

---

## 2. Bảng Phân Tích Chi Tiết Từng Khâu Core Pipeline (Total = 37,960 ms)

Đo đạc trên bài test [`ScanCorePipelineBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/pipeline/ScanCorePipelineBenchmarkTest.java) với **1.000.000 files**:

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

## 3. Tiến hóa Hiệu năng qua các Thế hệ Kiến trúc (1M Files)

```text
[Thế hệ 1: JDBC Batch Baseline]      ████████████████████████████████████████ 84.65s  (Bottleneck JDBC: 64.5s)
[Thế hệ 2: Set-based + COPY]         ██████████ 24.90s                                (Persistence: 4.2s)
[Thế hệ 3: Scan Core Full End-to-End]████████████████ 37.96s                          (All Phases: 26.3k files/s)
```

---

## 4. Chỉ mục Báo cáo Chi tiết

1. 📜 **[01-legacy-jdbc-batch-baseline.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/01-legacy-jdbc-batch-baseline.md)**: Phân tích nguyên nhân điểm nghẽn 64.5s ở tầng JDBC batching.
2. 💾 **[02-database-set-based-persistence.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/02-database-set-based-persistence.md)**: Phân rã chi phí ForeignKey, Unique Constraint, UUIDv7 và SQL Set-based.
3. 🚀 **[03-scan-core-pipeline-benchmark.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/03-scan-core-pipeline-benchmark.md)**: Báo cáo phân tích chi tiết từng khâu Core Pipeline 1.000.000 files (37.96s / 26.343 files/s).


