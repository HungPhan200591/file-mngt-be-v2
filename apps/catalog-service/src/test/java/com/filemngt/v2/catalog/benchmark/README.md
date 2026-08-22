# Catalog Benchmark Suite

## FT-057 — current benchmark contract

- Combined gate: [CatalogOperationEndToEndBenchmarkTest](./operation/CatalogOperationEndToEndBenchmarkTest.java)
- D1 direct-stage diagnostic: [CatalogOperationIngestBenchmarkTest](./operation/CatalogOperationIngestBenchmarkTest.java)
- D1 Kafka-to-stage diagnostic: [CatalogOperationKafkaPipelineBenchmarkTest](./operation/CatalogOperationKafkaPipelineBenchmarkTest.java)
- D2 reconciliation diagnostic: [CatalogOperationMergeBenchmarkTest](./operation/CatalogOperationMergeBenchmarkTest.java)
- Dashboard: [BENCHMARK_RESULTS.md](./BENCHMARK_RESULTS.md)
- Run report template: [04-ft057-bulk-reconciliation-data-plane.md](./results/04-ft057-bulk-reconciliation-data-plane.md)

Mỗi class chỉ chạy hai workload: **25K** rồi **1M input records**. Phase diagnostic để tìm điểm nghẽn;
chỉ combined gate mới được đối chiếu mục tiêu Catalog tối thiểu 30K, stretch 40K input records/s.

`CatalogOperationEndToEndBenchmarkTest` dùng Kafka input thật, typed stage, finalizer, operation relay và
`KafkaCatalogOutboxMessagePublisher` thật. Test chỉ hoàn tất khi operation là `CATALOG_COMMITTED`, tất cả
snapshot đã `published_at` và final watermark đã được broker acknowledge rồi durable mark. Nó log hai clock:

- `resumeToFinalAckMs`: clock gate có kiểm soát, bắt đầu sau assignment/seed/warm-up khi listener được `resume()`.
  Nó bắt đầu ngay trước first receive nên bảo thủ so với SLO Catalog receive-to-final-ack.
- `firstPersistToFinalAckMs`: từ `min(catalog_operation_discovery_input.received_at)` đến `published_at` của
  final watermark. Đây là phase diagnostic sau durable ingest, không thay thế SLO clock.

Seed Kafka, assignment, rebalance và warm-up không nằm trong throughput. PostgreSQL của combined gate chạy
cấu hình durability mặc định. Vì vậy không so kết quả này với D1/D2 diagnostic dùng `fsync=off`.

## Phase diagnostics

`CatalogOperationIngestBenchmarkTest` đo `stage.ingest` trực tiếp với bốn worker; không có Kafka, finalizer
hoặc relay. `CatalogOperationKafkaPipelineBenchmarkTest` đo backlog Kafka đã seed tới typed stage, topology
cố ý 8 partition / 8 consumer / poll-slice 5000 để thử giới hạn ingest; đây không phải baseline combined.
`CatalogOperationMergeBenchmarkTest` đo từ `APPROVAL_COMMITTED` tới complete workset, không gồm seed,
Kafka hay relay. Các test này log telemetry để tối ưu đúng phase, không claim SLO end-to-end.

## Historical baselines

- FT-054 legacy/direct canonical baseline: [result](./results/01-ft054-legacy-catalog-record-baseline.md)
- FT-055 Kafka backlog-drain evidence: [result](./results/02-ft055-kafka-backlog-drain.md)
- FT-056 V19–V22 merge evidence: [result](./results/03-ft056-set-based-cte-merge.md)

`CatalogOperationCoalescingBenchmarkTest` được giữ làm direct canonical baseline FT-054. Nó không chạy relay
và không còn performance gate 10 giây; không dùng nó để qualify FT-057.
