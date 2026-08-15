# Reference Capsule: BT-09G — Capacity Model & Benchmarking

> Trích xuất từ: `docs/reviews/2026-08-13-approve-5000-query-performance-assessment.md` (Capacity & Hạ tầng) & `07-performance-slo-and-benchmarks.md`.
> Phạm vi: Áp dụng cho kế hoạch benchmark thang đo 1K → 1M và đo lường tài nguyên.

---

## 1. Thang đo Scale Ladder (Tăng dần tải trọng)

Không bao giờ chạy ngay 1.000.000 records khi chưa vượt qua các nấc thang trước đó:

```text
Ladder 1: 1.000 records   -> Đo kiểm functional & correctness luồng bulk
Ladder 2: 5.000 records   -> Calibration rung để soi bottleneck đầu tiên
Ladder 3: 50.000 records  -> Thử tải trung bình, kiểm tra DB connection pool
Ladder 4: 250.000 records -> Thử tải nặng, kiểm tra WAL rate & Kafka lag
Ladder 5: 1.000.000 records -> Target workload chính thức
```

---

## 2. Phân bổ Ngân sách Latency (Latency Budget Target)

Đối với workload 1.000.000 records:

| Chặng xử lý | % Thời gian mục tiêu | Trọng tâm tối ưu |
| --- | --- | --- |
| **Scan Decision + Outbox Chunking** | 25% – 30% | JDBC batch, không hydrate entity, chunk 2.000 items. |
| **Kafka Relay & Outbox Drain** | 15% – 20% | Continuous drain, async non-blocking publish. |
| **Catalog Coalesce & Bulk DB** | 25% – 30% | In-memory coalesce theo subject, giảm 70% DB writes. |
| **Query Bulk Projection + cache switch** | 20% – 25% | Bulk COPY/Upsert, `subjectVersion` guard, `cacheGeneration` O(1). |
| **Tổng cộng tới `QUERY_DB_READY`** | **100%** | **Budget phân bổ; chưa phải runtime evidence** |

---

## 3. Các chỉ số quan trắc then chốt (Mandatory Metrics)

1. **Throughput (Records/giây)**: Đo tốc độ trung bình và peak của từng chặng.
2. **PostgreSQL Connection Pool**: Active connections vs Wait count (phải giữ wait count = 0).
3. **PostgreSQL WAL Rate & Lock Time**: Đảm bảo không nghẽn I/O đĩa cứng.
4. **Kafka Consumer Lag**: Lag của consumer group `catalog-service` và `query-service`.
5. **Outbox Backlog Age**: Thời gian một outbox event chờ từ lúc ghi DB đến lúc gửi lên Kafka.
6. **JVM Heap & GC Pause**: Đảm bảo không có Stop-the-World GC kéo dài quá 100ms.
