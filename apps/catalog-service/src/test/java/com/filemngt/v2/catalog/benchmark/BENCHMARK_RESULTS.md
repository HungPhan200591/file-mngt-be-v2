# Catalog Service Benchmark Results Dashboard

| Benchmark | Legacy 25K | Legacy 1M | Candidate FT054 | Status |
| --- | ---: | ---: | ---: | --- |
| FT-054 Catalog record processing | 59 rec/s (423.898 ms) | TIMED OUT (> 2m) | 4,325 rec/s (5,781 ms) / 1M TIMED OUT (> 5m) | Qualification thất bại; đã thêm V19 + phase timing, chưa chạy |

Chi tiết workload và boundary: [01-ft054-legacy-catalog-record-baseline.md](./results/01-ft054-legacy-catalog-record-baseline.md).

Test: [`CatalogLegacyRecordProcessingBenchmarkTest.java`](./legacy/CatalogLegacyRecordProcessingBenchmarkTest.java).
Candidate: [`CatalogOperationCoalescingBenchmarkTest.java`](./operation/CatalogOperationCoalescingBenchmarkTest.java).

Bằng chứng candidate mới nhất là run do người dùng cung cấp ngày 2026-08-20: calibration 25K hoàn tất trong
5.781 s (5,781 ms); qualification 1M không đạt `CATALOG_COMMITTED` trong năm phút và finalizer Catalog log retryable
`QueryTimeoutException`. Candidate test hiện ghi timing preparation, stage ingest, watermark và finalizer wait
cho lần chạy kế tiếp.

Không ghi claim throughput hoặc SLO trước khi có run manifest và số đo thật.
