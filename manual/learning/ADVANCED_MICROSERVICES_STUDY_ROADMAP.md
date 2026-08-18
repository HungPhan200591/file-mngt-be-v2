# Lộ trình study Microservices nâng cao — Senior Java

> Dự án thực hành: `file_mngt_microservice`. Mục tiêu: luyện ownership, transaction boundary, event delivery, failure model, vận hành và trade-off từ luồng V2 thật.

## Cách dùng lộ trình

Roadmap điều phối thứ tự học, không thay architecture, contract, context service, code hoặc [`docs/STATUS.md`](../../docs/STATUS.md). Mỗi bài đi theo:

```text
Scenario thật → Deep-dive → Summary → Question bank → failure test / evidence
```

- Deep-dive sở hữu giải thích và bằng chứng; summary không tự thêm claim mới; question bank chỉ sinh từ artifact trước.
- UC pack ở `use-cases/core-flows/`; SC pack ở `use-cases/scale-capacity/`. Đọc [System Primer](./system-primer/README.md) trước UC-01.
- Không học công nghệ trước failure mode thật; không dùng roadmap làm backlog implementation.

## Tiến độ ôn tập cá nhân

Đây là snapshot duy nhất về mức độ học cá nhân, không phải lịch sử điểm hay bằng chứng implementation. `Chưa xác nhận` nghĩa là có học liệu để ôn, nhưng chưa đủ căn cứ kết luận người học đã nắm. Dùng `$review-learning` để ôn và chỉ cập nhật hàng topic khi người học yêu cầu lưu kết quả.

| Topic | Học liệu owner | Mức hiện tại | Ôn gần nhất | Trọng tâm lần tới |
| --- | --- | --- | --- | --- |
| Domain, ownership và data flow | [System Primer](./system-primer/README.md) | Chưa xác nhận | — | Subject/Asset, service và database boundary |
| Scan preview và approval | [Scan service](./deep-dive/scan-service/00-overview.md) | Chưa xác nhận | — | Discovery/review, proposal và issue |
| Transactional Outbox | [Deep-dive](./deep-dive/transactional-outbox/README.md) | Chưa xác nhận | — | Local transaction, relay, duplicate và DLT |
| Virtual Threads | [Deep-dive](./deep-dive/virtual-threads/00-overview.md) | Chưa xác nhận | — | Pinning, no-pooling và throttling |
| Java Concurrency & Async | [Deep-dive](./deep-dive/java-concurrency/00-overview-mental-model.md) | Chưa xác nhận | — | CompletableFuture, ThreadPool, Locks & Memory |
| CQRS Read Projection | [Deep-dive](./deep-dive/cqrs-read-projection/README.md) | Chưa xác nhận | — | Version guard, hydration và reconciliation |
| Observability | [Deep-dive](./deep-dive/observability/README.md) | Chưa xác nhận | — | Metrics, logs, trace và incident flow |
| Database Internals (Storage & Query) | [Deep-dive](./deep-dive/database-internals/README.md) | Chưa xác nhận | — | WAL, Buffer Pool, Anti-Join, work_mem & EXPLAIN |
| UC-01 — Scan → Catalog ingestion | [Study pack](./use-cases/core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) | Chưa xác nhận | — | Ownership, outbox và idempotent consumer |
| SC-01 — Scan 1M filesystem entry | [Study pack](./use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/README.md) | Chưa xác nhận | — | Lease, checkpoint, chunk commit và backpressure |

## Lộ trình core theo dependency

| Thứ tự | Use case | Dịch vụ trọng tâm | Năng lực cần chứng minh | Trạng thái |
| --- | --- | --- | --- | --- |
| 0 | Domain, ownership, data flow | Toàn hệ thống | Subject/Asset, database và event boundary | Đọc primer |
| 1 | [UC-01 — Scan → Catalog canonical ingestion](./use-cases/core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) | Scan, Catalog | async job, review, outbox, idempotent consumer | Đang học |
| 2 | UC-02 — Catalog → Query projection | Catalog, Query | snapshot, version guard, replay/rebuild | Sau UC-01 |
| 3 | UC-03 — Query search/cache/reconciliation | Query | read model, degraded read, đo đạc | Sau UC-02 |
| 4 | UC-04 — Media processing pipeline | Worker, Catalog, Query | work queue, bounded I/O, completion | Khi FT013 mở |
| 5 | UC-05 — Observability/performance E2E | Toàn hệ thống | metrics, logs, trace, profiling | Sau core flow |
| 6 | UC-06 — Import, replay, reconciliation V1 | Catalog, Query | batch idempotency, checkpoint, rollback an toàn | Phase 7 |

Thứ tự này đi từ discovery/review đến canonical write, rồi read model và worker. Không bắt đầu bằng upload, Saga hay cache vì chúng chưa là boundary trung tâm của V2.

## Scale & Capacity Track

Sau khi một core flow đúng ở quy mô nhỏ, SC hỏi liệu flow đó còn đúng khi volume, traffic và thời gian chạy tăng. Trước mỗi SC phải chốt volume/growth, traffic mix, SLO, retention, resource limit và correctness khi partial failure; “1 triệu” là quy mô lab, không phải năng lực đã xác nhận.

| ID | Bài toán | Sau | Câu hỏi Senior phải bảo vệ |
| --- | --- | --- | --- |
| [SC-01](./use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/README.md) | Scan 1M filesystem entry | UC-01 | Directory walk bounded memory, progress/checkpoint, batch persistence và backpressure ra sao? |
| SC-02 | Import/backfill 1M record V1 | UC-01 + UC-02, Phase 7 | Dry-run, batch/restart, dedupe và đối soát mà không ghi DB V1 thế nào? |
| SC-03 | Catalog 100M–1B asset | UC-01 | Access pattern, index/lifecycle, uniqueness và migration còn đúng ra sao? |
| SC-04 | Query/search lớn | UC-02 + UC-03 | Pagination, index/cache, hot key và degraded mode bảo vệ SLO thế nào? |
| SC-05 | Kafka/outbox backlog/replay | UC-01 + UC-02 | Partition, lag, retry/DLT, ordering và duplicate được đo/khôi phục thế nào? |
| SC-06 | Hàng triệu media job | UC-04 | Concurrency, queue age, retry budget và storage protection thế nào? |

Chỉ sau SC-01 đến SC-04 mới thảo luận scale-out, sharding hoặc multi-region bằng dữ liệu; không chọn chúng làm điểm xuất phát.

## Các use case cần nhớ

### UC-01 — Scan → Catalog canonical ingestion

Admin scan một `rootKey`, review proposal/issue, rồi approve. Scan ghi decision và outbox cùng local transaction; relay phát `media.file.discovered.v1`; Catalog dedupe event và upsert Subject/Asset trong `catalog_db`.

Câu hỏi trọng tâm: vì sao Scan không ghi trực tiếp Catalog; outbox bảo đảm gì và không bảo đảm gì; duplicate/out-of-order xử lý ở đâu; Kafka/Catalog hỏng thì vận hành/retry/DLT ra sao. Xem study pack UC-01 để có boundary, failure drill và evidence.

### UC-02 và UC-03 — Catalog → Query

UC-02 bảo vệ projection correctness: snapshot event, `eventId` dedupe, `subjectVersion` guard, retry/DLT và replay. UC-03 mới tối ưu read: search, Redis cache-aside, hydration, reconciliation và benchmark; cache hoặc Elasticsearch hỏng không được làm canonical data sai.

### UC-04 đến UC-06

- UC-04: Worker stateless đọc file trong root an toàn, giới hạn I/O, publish deterministic completion về Catalog rồi Query hội tụ.
- UC-05: trace/metric/log có ngữ cảnh E2E; alert/SLO, k6 và profiling chỉ làm khi flow đã có baseline.
- UC-06: inventory V1 read-only, dry-run, checkpoint, idempotency và reconciliation; không import thật chỉ để tạo bài tập.

## Lab để sau

Resumable upload, gateway security/rate limit, circuit breaker/Saga/chaos/Kubernetes, Debezium/Schema Registry/Kafka Streams chỉ mở khi có boundary, threat model, SLO hoặc compatibility problem tương ứng; không là prerequisite cho flow core.

## Definition of done cho một study pack

- Có scenario, invariant/ownership, contract hoặc code evidence và failure model.
- Có deep-dive, summary, question bank và evidence/test phù hợp; không có port/event/API/hiệu năng chưa kiểm chứng.
- Chỉ mở feature ADLC khi study chỉ ra thay đổi business boundary, contract hoặc implementation cần thực hiện.
