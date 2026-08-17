# 📊 Scan Service Benchmark Results Dashboard

Tài liệu này tổng hợp **bảng chỉ số tóm tắt (Executive Summary)** của tất cả các đợt đo đạc hiệu năng trên hệ thống `scan-service` cho tải trọng **1.000.000 bản ghi**. Chi tiết phương pháp, biểu đồ và phân tích chuyên sâu được dẫn link trực tiếp tới từng báo cáo riêng biệt.

---

## 1. Bảng Tổng hợp Chỉ số Toàn cảnh (Summary Matrix)

FT-049 scale qualification (1K → 1M repeated matrix) được **deferred**; các số liệu bên dưới không được coi là
qualification theo phần cứng/SLO. Evidence hiện hành chỉ thuộc các benchmark feature tương ứng và giữ nguyên boundary
scan-core, loại filesystem/Catalog I/O theo mô tả từng report.

| Mã bài đo | Tên Hạng mục Đo | Phạm vi / Công nghệ | Workload | Thời gian đo | Tốc độ (Throughput) | Báo cáo Chi tiết |
|---|---|---|:---:|:---:|:---:|:---:|
| **`BENCH-01`** | **Legacy JDBC Batch Baseline** | JDBC Batch 50k / PostgreSQL | 1.000.000 diff | **43.45s – 84.65s** | ~23.000 files/s | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/01-legacy-jdbc-batch-baseline.md) |
| **`BENCH-02`** | **Database Set-based Persistence** | Direct COPY + SQL Set-based | 1.000.000 diff | **18.67s – 19.34s** | ~53.000 rows/s | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/02-database-set-based-persistence.md) |
| **`BENCH-03`** | **Scan Core End-to-End Pipeline** | Full Service (`scanService.start`) | 1.000.000 files | **< 25s (Cold: ~24.85s, Warm: ~8.11s)** | **~40.240 – 123.270 files/s** | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/03-scan-core-pipeline-benchmark.md) |
| **`BENCH-04`** | **Approval Decision & Outbox** | Legacy JPA approve-all baseline → chunked replacement | 25.000 decisions (calibration) | *(Chưa chạy)* | *(Pending FT-045)* | [Test approval legacy](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/approval/legacy/LegacyScanDecisionBatchBenchmarkIT.java) |

---

## 2. Bảng Phân Tích Chi Tiết Từng Khâu Core Pipeline (Cold Baseline < 25s, Total ≈ 24,850 ms)

Đo đạc trên bài test [`ScanCorePipelineBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/preview/ScanCorePipelineBenchmarkTest.java) với **1.000.000 files** (áp dụng FT-047 Direct Cold Bypass + FT-048 25k Pipelining trên Desktop PC):

| Khâu thực thi | Chi tiết tác vụ | Thời gian (ms) | % Thời gian | Tốc độ tương đương |
| :--- | :--- | :---: | :---: | :---: |
| **1. Phân tích Regex & Keyset** | 8 Virtual Threads parse 1M files + streaming | **~7,200 ms** | **29.0%** | ~138,800 files/s |
| **2. Ghi Proposals vào DB** | PostgreSQL Binary `COPY scan_proposal` (990.000 dòng) | **~6,800 ms** | **27.4%** | ~145,500 proposals/s |
| **3. Ghi Inventory vào DB** | Direct SQL `INSERT INTO scan_file_inventory` (1M dòng) | **~5,100 ms** | **20.5%** | ~196,000 rows/s |
| **4. Discovery Stream Staging** | In-Memory Stream $\to$ COPY `scan_inventory_stage` | **~4,500 ms** | **18.1%** | ~222,000 files/s |
| **5. Finalize, Issue & Checkpoint**| Ghi 10k Issues, commit chunk 25k, dọn staging | **~1,250 ms** | **5.0%** | — |
| **TỔNG CỘNG (COLD)** | **Xử lý toàn diện 1.000.000 Files (PC)** | **~24,850 ms** | **100%** | **~40,240 files/s** |
| *(Tham chiếu WARM UNCHANGED)* | *1M files không đổi (Không ghi proposals/inventory)* | *~8,112 ms* | *—* | *> 123,000 files/s* |

> ℹ️ **Ghi chú môi trường:** Trên Laptop (do giới hạn TDP nhiệt độ CPU và tốc độ ghi liên tục/fsync của SSD di động), thời gian COLD đạt ~44.2s (~22.5k files/s) và UNCHANGED đạt ~11.3s (~88k files/s).

---

## 3. Tiến hóa Hiệu năng qua các Thế hệ Kiến trúc (1M Files)

```text
[Thế hệ 1: JDBC Batch Baseline]       ████████████████████████████████████████ 84.65s (Bottleneck JDBC: 64.5s)
[Thế hệ 2: Set-based + COPY]          ██████████ 24.90s                               (Persistence diff: 4.2s)
[Thế hệ 3: Scan Core FT-046 Baseline] ████████████████ 37.96s                         (Diff Staging + Chunk 100k)
[Thế hệ 4: Scan Core FT-047/048 (PC)] ██████████ 24.85s                               (Bypass Cold Stage + Chunk 25k, <25s)
```

---

## 4. Chỉ mục Báo cáo Chi tiết

1. 📜 **[01-legacy-jdbc-batch-baseline.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/01-legacy-jdbc-batch-baseline.md)**: Phân tích nguyên nhân điểm nghẽn 64.5s ở tầng JDBC batching.
2. 💾 **[02-database-set-based-persistence.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/02-database-set-based-persistence.md)**: Phân rã chi phí ForeignKey, Unique Constraint, UUIDv7 và SQL Set-based.
3. 🚀 **[03-scan-core-pipeline-benchmark.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/03-scan-core-pipeline-benchmark.md)**: Báo cáo phân tích chi tiết từng khâu Core Pipeline 1.000.000 files (< 25s Cold / 8.1s Warm).

