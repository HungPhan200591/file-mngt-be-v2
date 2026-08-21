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

## FT-056 V19 merge baseline

| Workload | mergeMs | Throughput | pageExec | Status |
| --- | ---: | ---: | --- | --- |
| 2.500 subjects (25K events) | 2.032 s | 1.230 subject/s | avg 106ms, p95 155ms, 64 pages | Local evidence 2026-08-21 |
| 100.000 subjects (1M events) | TIMED OUT (> 2 min) | — | — | JUnit timeout 2 min |

Chi tiết topology và boundary: [03-ft056-set-based-cte-merge.md](./results/03-ft056-set-based-cte-merge.md).
Test: [`CatalogOperationMergeBenchmarkTest.java`](./operation/CatalogOperationMergeBenchmarkTest.java).

`mergeMs` gồm persist equality gate, không gồm seed ingest, Kafka hay relay. Không so isolated ingest và không claim gate D2 (`< 5 ms/page`, 100K `<= 5 s`).

## FT-055 Kafka backlog drain

| Workload | drainMs | Throughput | Telemetry | Status |
| --- | ---: | ---: | --- | --- |
| 25K (2.500 subjects) | 1.164 s | 21.478 rec/s | slices=16, avgPerSlice=274.9ms, stageSql 63.6% | Local evidence 2026-08-21 |
| 1M (100.000 subjects) | 24.527 s | 40.771 rec/s | slices=232, avgPerSlice=643.7ms, stageSql 82.0% | Local evidence 2026-08-21 |

Chi tiết topology, boundary và log: [02-ft055-kafka-backlog-drain.md](./results/02-ft055-kafka-backlog-drain.md).
Test: [`CatalogOperationKafkaPipelineBenchmarkTest.java`](./operation/CatalogOperationKafkaPipelineBenchmarkTest.java).

`drainMs` không gồm assignment/produce/rebalance. Topology 8 partition / 8 consumer / slice 5000, Testcontainers `fsync=off`. Không so với isolated ingest và không claim SLO `QUERY_DB_READY`.
