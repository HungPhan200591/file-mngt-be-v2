# ⚡ Benchmark Chi tiết: Phase 4 CPU Parallel Analyzer & Virtual Threads

- **Mã bài đo**: `BENCH-03-CPU-PARALLEL-ANALYZER`
- **Class thực thi**: [`ScanParallelAnalyzerBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/pipeline/ScanParallelAnalyzerBenchmarkTest.java)
- **Workload**: 1.000.000 Synthetic Files trong RAM (`SyntheticScanItemGenerator`)

- **Môi trường**: Amazon Corretto 25.0.4, 8 Virtual Threads Partitions

---

## 1. Kết quả Chi tiết Đo đạc Thực tế

| Chỉ số Đo đạc | Giá trị Thực tế | Ghi chú |
|---|---:|---|
| **Tổng số bản ghi phân tích** | **1.000.000 files** | Sinh bằng Synthetic Generator |
| **Số luồng phân vùng** | **8 Virtual Threads** | Phân bổ đều trên CPU |
| **Thời gian thực thi** | **2.596 – 3.019 ms** | **~2.6 – 3.0 giây** |
| **Tốc độ xử lý (Throughput)** | **331.236 – 385.208 files/giây** | Cực kỳ nhanh |
| **Độ trễ trung bình mỗi file** | **2.60 – 3.02 µs/file** | Micro-giây |
| **Hợp lệ (Proposals tạo ra)** | **990.000 items (99%)** | Đầy đủ Evidence JSON |
| **Lỗi/Mơ hồ (Issues phát hiện)** | **10.000 items (1%)** | Phân loại chính xác |

---

## 2. Phân tích Kỹ thuật & Khuyến nghị
- **Năng lực tính toán của Java 25**: Phân tích cú pháp Regex và bóc tách metadata trong RAM diễn ra gần như tức thì ($2.6\mu\text{s}$/file).
- **Điểm phân hóa so với Full Pipeline (18.5s)**:
  - Khi chạy trong RAM liên tục 1 cục (Monolithic In-Memory): chỉ mất $3.0\text{s}$.
  - Khi chạy trong Full Pipeline thực tế: việc chia nhỏ 200 chunks (5.000 items/chunk) gây overhead truy vấn JDBC và GC overhead giữa các vòng lặp $\implies$ Khẳng định tính đúng đắn của việc **nâng kích thước Chunk lên 25.000 items** ở các lát cắt tiếp theo.
