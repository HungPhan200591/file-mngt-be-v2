# Catalog Benchmark Suite

## FT-056 V19 merge baseline

- Test: [CatalogOperationMergeBenchmarkTest](./operation/CatalogOperationMergeBenchmarkTest.java)
- Dashboard: [BENCHMARK_RESULTS.md](./BENCHMARK_RESULTS.md)

Đo `catalog_finalize_operation_page` (V19 temp-DDL) trên finalizer Spring thật, page size `500`, 4 worker.
Seed ingest không nằm trong `mergeMs`. Await workset `COMPLETED`, không đợi `CATALOG_COMMITTED` và không
claim gate D2. Result report: [03-ft056-set-based-cte-merge.md](./results/03-ft056-set-based-cte-merge.md).
Run 2026-08-21: 2.500 subject `mergeMs=2.032 s` (pageExec avg 106ms); 100.000 subject timeout `> 2 min`.

## FT-055 Kafka backlog drain

- Test: [CatalogOperationKafkaPipelineBenchmarkTest](./operation/CatalogOperationKafkaPipelineBenchmarkTest.java)
- Isolated ingest (cùng feature, khác boundary): [CatalogOperationIngestBenchmarkTest](./operation/CatalogOperationIngestBenchmarkTest.java)
- Result report: [02-ft055-kafka-backlog-drain.md](./results/02-ft055-kafka-backlog-drain.md)
- Dashboard: [BENCHMARK_RESULTS.md](./BENCHMARK_RESULTS.md)

Đo `CatalogOperationBatchConsumer` drain backlog đã seed sẵn trên Kafka Testcontainer. `drainMs` bắt đầu lúc
`resume()` sau warm-up; seed Kafka và assignment không nằm trong throughput. Topology run 2026-08-21:
8 partition / 8 consumer / `max.poll.records=5000` / `slice-records=5000`. Không so với isolated ingest
(4 worker, gọi `stage.ingest` trực tiếp) và không claim SLO `QUERY_DB_READY`.

## FT-054

- Legacy baseline: [CatalogLegacyRecordProcessingBenchmarkTest](./legacy/CatalogLegacyRecordProcessingBenchmarkTest.java)
- Candidate: [CatalogOperationCoalescingBenchmarkTest](./operation/CatalogOperationCoalescingBenchmarkTest.java)
- Liveness guard: [`CatalogOperationFinalizerTest`](../application/operation/CatalogOperationFinalizerTest.java)
- Result report: [01-ft054-legacy-catalog-record-baseline.md](./results/01-ft054-legacy-catalog-record-baseline.md)
- Dashboard: [BENCHMARK_RESULTS.md](./BENCHMARK_RESULTS.md)

Baseline đo 25K và 1M event bằng legacy record-at-a-time path. Candidate FT054 chỉ được thêm vào dashboard
sau khi có cùng workload, payload và run manifest; không so sánh khác boundary.

Candidate log tách `prepareMs`, `stageIngestMs`, `watermarkBuildMs`, `watermarkPersistMs` và
`finalizerWaitMs`. Khi không hội tụ, test log durable operation status, received/completed/snapshot counters
và số lane chưa hoàn tất. Deadline diagnostic tính từ đầu benchmark (25K: 90s; 1M: 210s) để chừa headroom
trước JUnit timeout; các số này là bằng chứng chẩn đoán, không thay thế `EXPLAIN (ANALYZE, BUFFERS, WAL)`.
