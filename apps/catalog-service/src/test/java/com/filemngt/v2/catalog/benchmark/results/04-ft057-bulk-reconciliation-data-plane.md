# FT-057 — Combined Catalog Bulk Reconciliation Data Plane

Status: `READY TO MEASURE — no runtime result recorded`

Test: [CatalogOperationEndToEndBenchmarkTest](../operation/CatalogOperationEndToEndBenchmarkTest.java)

## Boundary

Kafka discovery records và một `APPROVAL_COMMITTED` watermark được seed sau khi hai Catalog consumer group đã
assigned và pause. Measurement bắt đầu khi listener `resume()` và kết thúc khi final output watermark đã nhận
Kafka broker acknowledgement, relay set `published_at`, trigger chuyển operation sang `CATALOG_COMMITTED`.

Report bắt buộc ghi cả hai clock:

| Clock | Ý nghĩa | Dùng cho SLO? |
| --- | --- | --- |
| `resumeToFinalAckMs` | Controlled lab drain, không gồm seed/assignment/warm-up; bắt đầu ngay trước first receive | Có, clock gate bảo thủ cho input records/s Catalog |
| `firstPersistToFinalAckMs` | input immutable đầu tiên persisted đến final watermark broker-ack durable mark | Không, chỉ để bóc phase sau durable ingest |

## Fixed manifest

| Thành phần | Cấu hình |
| --- | --- |
| Workload | 25.000 và 1.000.000 input records; 10 assets/subject |
| Kafka input | 4 discovery partitions, Catalog operation consumer concurrency 4, poll/slice 2.000 |
| Reconciliation | 4 finalizer workers, 16 reconcile units |
| Operation relay | 64 lanes, 4 workers, fetch 2.000, max in-flight 500 |
| PostgreSQL | Testcontainers `postgres:18.0-alpine`, default durability; không `tmpfs`, `fsync=off` hoặc `synchronous_commit=off` |
| Output proof | subject snapshots và exact one `CATALOG_COMMITTED` watermark đều có `published_at` |

## Result template

| Run | Input | Subjects | Outputs | resumeToFinalAckMs | firstPersistToFinalAckMs | Durable rec/s | 30K gate | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| Calibration | 25K | — | — | — | — | — | — | Pending |
| Qualification 1 | 1M | — | — | — | — | — | — | Pending |
| Qualification 2 | 1M | — | — | — | — | — | — | Pending |
| Qualification 3 | 1M | — | — | — | — | — | — | Pending |

Không kết luận production P95/P99 từ Testcontainers laptop. Khi một run timeout hoặc assertion failure, lưu log
exact failed phase, `CatalogOperationIngestTelemetry`, `CatalogOperationFinalizerTelemetry`, DB/WAL/lock evidence
và resource manifest trước khi tối ưu hoặc đổi timeout.
