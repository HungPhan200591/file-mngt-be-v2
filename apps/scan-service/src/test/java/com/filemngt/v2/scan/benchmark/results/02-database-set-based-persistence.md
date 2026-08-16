# 💾 Benchmark Chi tiết: Database Set-based Persistence (FT-031)

- **Mã bài đo**: `BENCH-02-DB-SET-BASED`
- **Class thực thi**: [`SetBasedReconciliationWriteBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/pipeline/SetBasedReconciliationWriteBenchmarkTest.java)
- **Workload**: 1.000.000 diff rows (1M inventory, 900k proposals, 100k issues)

- **Môi trường**: PostgreSQL Testcontainers (`postgres:18.0-alpine`), Java 25

---

## 1. Kết quả Chi tiết Đo đạc

### 1.1 Chi tiết Thời gian Set-based SQL
| Bước xử lý | Thời gian | Ghi chú |
|---|---:|---|
| Seed Diff Stage | 2.523 – 2.539s | Nạp bảng trung gian |
| Ghi Inventory (1.000.000 rows) | 5.264 – 5.436s | `INSERT INTO scan_file_inventory SELECT ...` |
| Ghi Proposals (900.000 rows) | 12.006 – 12.209s | Kèm Unique + FK Constraint |
| Ghi Issues (100.000 rows) | 1.401 – 1.697s | Phân loại file lỗi |
| **Tổng Transaction Persistence** | **18.674 – 19.348s** | **Giảm 57% so với JDBC Batch** |

### 1.2 Phân tích Rào cản Invariant trên bảng `scan_proposal` (900k rows)
| Biến thể kiểm nghiệm | Thời gian | Chênh lệch so với Baseline |
|---|---:|---:|
| **Baseline (Đầy đủ FK + Unique Key)** | **12.210s** | — |
| Bỏ Foreign Key (FK), giữ Unique Key | 4.745s | **-7.465s (~61% nhanh hơn)** |
| Giữ FK, bỏ Unique Key | 11.644s | -0.566s (~4.6%) |
| **UUIDv7 Native + FK + Unique Key** | **11.215s** | **-0.995s (~8.1%)** |

---

## 2. Kết luận & Quyết định Kiến trúc
- **Foreign Key (FK)** là chi phí lớn nhất chiếm tới 61% thời gian ghi proposal do PostgreSQL phải kiểm tra khóa ngoại sang `scan_run`.
- **UUIDv7 Native** tăng tính tuần tự (Locality) cho B-Tree Index, giúp giảm page-split và tăng tốc độ ghi thêm 8.1%.
- Dữ liệu Staging trong PostgreSQL được xử lý bằng SQL tập hợp (`INSERT ... SELECT`), loại bỏ hoàn toàn việc đọc dữ liệu ngược về Java rồi mới ghi lại DB.
