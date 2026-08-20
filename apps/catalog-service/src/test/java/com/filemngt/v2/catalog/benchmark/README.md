# Catalog Benchmark Suite

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
