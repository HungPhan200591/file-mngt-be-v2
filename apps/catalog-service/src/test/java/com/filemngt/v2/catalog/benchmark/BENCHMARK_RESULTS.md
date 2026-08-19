# Catalog Service Benchmark Results Dashboard

| Benchmark | Legacy 25K | Legacy 1M | Candidate FT054 | Status |
| --- | ---: | ---: | ---: | --- |
| FT-054 Catalog record processing | 59 rec/s (423.898 ms) | TIMED OUT (> 2m) | Chưa chạy | Candidate source ready; qualification pending |

Chi tiết workload và boundary: [01-ft054-legacy-catalog-record-baseline.md](./results/01-ft054-legacy-catalog-record-baseline.md).

Test: [`CatalogLegacyRecordProcessingBenchmarkTest.java`](./legacy/CatalogLegacyRecordProcessingBenchmarkTest.java).
Candidate: [`CatalogOperationCoalescingBenchmarkTest.java`](./operation/CatalogOperationCoalescingBenchmarkTest.java).

Không ghi claim throughput hoặc SLO trước khi có run manifest và số đo thật.
