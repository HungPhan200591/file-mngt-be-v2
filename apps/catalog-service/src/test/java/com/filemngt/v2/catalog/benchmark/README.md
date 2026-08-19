# Catalog Benchmark Suite

## FT-054

- Legacy baseline: [CatalogLegacyRecordProcessingBenchmarkTest](./legacy/CatalogLegacyRecordProcessingBenchmarkTest.java)
- Candidate: [CatalogOperationCoalescingBenchmarkTest](./operation/CatalogOperationCoalescingBenchmarkTest.java)
- Result report: [01-ft054-legacy-catalog-record-baseline.md](./results/01-ft054-legacy-catalog-record-baseline.md)
- Dashboard: [BENCHMARK_RESULTS.md](./BENCHMARK_RESULTS.md)

Baseline đo 25K và 1M event bằng legacy record-at-a-time path. Candidate FT054 chỉ được thêm vào dashboard
sau khi có cùng workload, payload và run manifest; không so sánh khác boundary.
