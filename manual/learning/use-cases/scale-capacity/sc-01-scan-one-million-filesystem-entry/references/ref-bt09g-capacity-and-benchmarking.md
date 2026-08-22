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

Đối với workload 1.000.000 records, SLI-03 end-to-end tới `QUERY_DB_READY` có P95 target 60 giây.
Catalog có phase SLI-03C riêng: tối thiểu 30K input records/s (`<= 33,334s`), stretch 40K/s (`<= 25s`).

| Chặng xử lý | P95 budget | Trọng tâm tối ưu |
| --- | ---: | --- |
| **Scan Decision + Outbox Chunking** | 5,000s | Bounded chunk, không hydrate entity, decision/outbox atomic. |
| **Scan Kafka Relay & Outbox Drain** | 4,000s | Continuous drain, bounded async publish. |
| **Catalog first receive → final broker ack** | 33,334s | Immutable typed ingest, sealed workset, coarse-unit set-based reconciliation, indexed sliding relay. |
| **Query Bulk Projection + cache switch** | 7,000s | Bulk COPY/Upsert, `subjectVersion` guard, `cacheGeneration` O(1). |
| **Queue/I/O/GC reserve** | 10,666s | Variance budget, không dùng để che backlog tăng vô hạn. |
| **Tổng cộng tới `QUERY_DB_READY`** | **60,000s** | **Budget phân bổ; chưa phải runtime evidence** |

---

## 3. Các chỉ số quan trắc then chốt (Mandatory Metrics)

1. **Throughput (Records/giây)**: Catalog dùng input count/tổng Catalog clock; output snapshot rate báo riêng,
   không trộn hai cardinality.
2. **PostgreSQL Connection Pool**: Active connections vs Wait count (phải giữ wait count = 0).
3. **PostgreSQL WAL Rate & Lock Time**: Đảm bảo không nghẽn I/O đĩa cứng.
4. **Kafka Consumer Lag**: Lag của consumer group `catalog-service` và `query-service`.
5. **Outbox Backlog Age**: Thời gian một outbox event chờ từ lúc ghi DB đến lúc gửi lên Kafka.
6. **JVM Heap & GC Pause**: Đảm bảo không có Stop-the-World GC kéo dài quá 100ms.
