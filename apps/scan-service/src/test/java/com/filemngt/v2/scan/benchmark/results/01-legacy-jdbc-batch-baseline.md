# 📜 Benchmark Chi tiết: Legacy JDBC Batch Baseline (FT-028)

- **Mã bài đo**: `BENCH-01-LEGACY-JDBC`
- **Class thực thi**: [`JdbcBatchReconciliationWriteBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/legacy/JdbcBatchReconciliationWriteBenchmarkTest.java)
- **Workload**: 1.000.000 diff rows (1M inventory, 900k proposals, 100k issues)

- **Môi trường**: PostgreSQL Testcontainers (`postgres:18.0-alpine`), Java 25

---

## 1. Kết quả Chi tiết Đo đạc

### 1.1 Full Scan Pipeline (Kiến trúc cũ trước tối ưu)
| Giai đoạn | Thời gian | Tỷ trọng |
|---|---:|---:|
| 1. Discovery + Staging COPY | 5.563s | 6.6% |
| 2. Materialize Diff | 2.888s | 3.4% |
| 3. Analyze | 6.919s | 8.2% |
| **4. Persistence (JDBC Batch)** | **64.508s** | **76.2% (BOTTLENECK)** |
| 5. Finalize | 1.971s | 2.3% |
| **TỔNG CỘNG 1M FILES** | **84.651s** | **100%** |

### 1.2 Chi tiết JDBC Batch 50.000 rows/batch
| Phép đo | Kết quả | Ghi chú |
|---|---:|---|
| **JDBC Batch thường** | 44.557s | Read: 32–41ms, Tx: ~1.8–2.1s mỗi batch |
| **Bật `reWriteBatchedInserts=true`** | 43.454s | Cải thiện 1.103s (~2.5%) |

---

## 2. Kết luận & Điểm nghẽn (Bottleneck)
- Round-trip mạng JDBC không phải là nguyên nhân chính gây chậm (bật rewrite chỉ tăng 2.5%).
- Chi phí ghi từng dòng (`Row-by-row`), cập nhật chỉ mục B-Tree và ghi nhật ký WAL trong transaction chính là nguyên nhân ngốn 64.5s.
- **Giải pháp chuyển đổi**: Chuyển sang Direct COPY nhị phân và SQL Set-based trong FT-031.
