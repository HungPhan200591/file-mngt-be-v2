# 📊 Scan Service Benchmark Results Dashboard

Tài liệu này tổng hợp **bảng chỉ số tóm tắt (Executive Summary)** của tất cả các đợt đo đạc hiệu năng trên hệ thống `scan-service` cho tải trọng **1.000.000 bản ghi**. Chi tiết phương pháp, biểu đồ và phân tích chuyên sâu được dẫn link trực tiếp tới từng báo cáo riêng biệt.

---

## 1. Bảng Tổng hợp Chỉ số Toàn cảnh (Summary Matrix)

| Mã bài đo | Tên Hạng mục Đo | Phạm vi / Công nghệ | Workload | Thời gian đo | Tốc độ (Throughput) | Báo cáo Chi tiết |
|---|---|---|:---:|:---:|:---:|:---:|
| **`BENCH-01`** | **Legacy JDBC Batch Baseline** | JDBC Batch 50k / PostgreSQL | 1.000.000 diff | **43.45s – 84.65s** | ~23.000 files/s | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/01-legacy-jdbc-batch-baseline.md) |
| **`BENCH-02`** | **Database Set-based Persistence** | Direct COPY + SQL Set-based | 1.000.000 diff | **18.67s – 19.34s** | ~53.000 rows/s | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/02-database-set-based-persistence.md) |
| **`BENCH-03`** | **CPU Parallel Analyzer (Phase 4)** | Java 25 Virtual Threads in RAM | 1.000.000 files | **2.59s – 3.01s** | ~331.000 – 385.000 files/s | [👉 Xem chi tiết](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/03-phase4-parallel-analyzer-cpu.md) |
| **`BENCH-04`** | **Scan Decision & Outbox Chunking** | Keyset 25k chunks / Tx boundary | 1.000.000 decisions | *(Đang triển khai)* | *(Pending FT-045)* | *(Sắp bổ sung)* |

---

## 2. Tiến hóa Hiệu năng qua các Thế hệ Kiến trúc (1M Files)

```text
[Thế hệ 1: JDBC Batch Baseline]      ████████████████████████████████████████ 84.65s  (Bottleneck JDBC: 64.5s)
[Thế hệ 2: Set-based + COPY]         ██████████ 24.90s                                (Persistence: 4.2s)
[Thế hệ 3: Pure CPU Analyzer in RAM] █ 3.01s                                          (Throughput: >330k files/s)
                                     ────────────────────────────────────────────────
                                     Target SLA SC-01: < 30.0s (ĐÃ ĐẠT CHUẨN)
```

---

## 3. Chỉ mục Báo cáo Chi tiết

1. 📜 **[01-legacy-jdbc-batch-baseline.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/01-legacy-jdbc-batch-baseline.md)**: Phân tích nguyên nhân điểm nghẽn 64.5s ở tầng JDBC batching.
2. 💾 **[02-database-set-based-persistence.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/02-database-set-based-persistence.md)**: Phân rã chi phí ForeignKey, Unique Constraint, UUIDv7 và SQL Set-based.
3. ⚡ **[03-phase4-parallel-analyzer-cpu.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/03-phase4-parallel-analyzer-cpu.md)**: Đánh giá năng lực xử lý CPU 8 Virtual Threads và micro-benchmark phân tích Regex trong RAM.
