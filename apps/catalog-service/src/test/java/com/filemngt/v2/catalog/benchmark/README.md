# Catalog Benchmark Suite

## FT-060 — current physical candidate

- Combined gate: [CatalogOperationEndToEndBenchmarkTest](./operation/CatalogOperationEndToEndBenchmarkTest.java)
- D1 direct-stage diagnostic: [CatalogOperationIngestBenchmarkTest](./operation/CatalogOperationIngestBenchmarkTest.java)
- D1 Kafka-to-stage diagnostic: [CatalogOperationKafkaPipelineBenchmarkTest](./operation/CatalogOperationKafkaPipelineBenchmarkTest.java)
- D2 reconciliation diagnostic: [CatalogOperationMergeBenchmarkTest](./operation/CatalogOperationMergeBenchmarkTest.java)
- Dashboard: [BENCHMARK_RESULTS.md](./BENCHMARK_RESULTS.md)
- Current physical-feasibility report: [07-ft060-bounded-upsert-parallelism.md](./results/07-ft060-bounded-upsert-parallelism.md)
- Sequential baseline: [06-ft059-sequential-physical-feasibility.md](./results/06-ft059-sequential-physical-feasibility.md)
- Previous combined reliability report: [05-ft058-reliability-hardening.md](./results/05-ft058-reliability-hardening.md)

Combined gate chạy ba workload: **25K**, **250K**, rồi **1M input records**. Consumer được assignment ổn định
rồi chạy liên tục trong lúc test publish discovery, đủ 64 logical-shard completion marker và global watermark
FT-059. Phase diagnostic để tìm điểm nghẽn; chỉ combined gate được đối chiếu release deadline 1M/120 giây.
Mức tối thiểu là 8.333 input records/s; 30K input records/s là stretch indicator.

`CatalogOperationEndToEndBenchmarkTest` dùng Kafka input thật, shard-completion marker, typed stage, bounded-page
finalizer, operation relay và
`KafkaCatalogOutboxMessagePublisher` thật. Test chỉ hoàn tất khi operation là `CATALOG_COMMITTED`, tất cả
snapshot đã `published_at` và final watermark đã được broker acknowledge rồi durable mark. Nó log một clock:

- `pipelineToFinalAckMs`: bắt đầu trước khi publish discovery đầu tiên và kết thúc sau final watermark
  broker-ack/durable mark. Clock gồm cả JSON/Kafka seed nên bảo thủ hơn SLO first-receive-to-final-ack.

Chỉ assignment ban đầu nằm ngoài throughput. Benchmark không pause/resume consumer và mặc định dùng một ingest
consumer, một finalizer worker, một shard seal mỗi tick để ưu tiên liveness có thể lặp lại. Mỗi run dùng
Testcontainers mới; PostgreSQL chạy cấu hình durability mặc định. Vì vậy không so kết quả này với D1/D2 diagnostic
dùng `fsync=off`.

`CatalogSequentialPhysicalFeasibilityBenchmarkTest` là diagnostic 1M riêng: production ingest tuần tự,
benchmark-only set-based reduction/materialization, bounded `1/2/4` bulk-upsert workers, full outbox snapshot
và production relay immediate-ack chạy theo barrier. Nó thu
PostgreSQL WAL/I/O/temp/lock cùng host CPU và JVM heap/GC để phân biệt physical ceiling với orchestration contention;
không thay combined gate và không phải production SQL qualification. FT-060 chọn hai workers làm measured best
nhưng vẫn fail 120 giây; bốn workers scale âm ở 1M.

Reset của combined benchmark dùng ordered `DELETE`, không dùng `TRUNCATE ... CASCADE`: scheduler vẫn hoạt động
khi `@BeforeEach` chạy, còn `TRUNCATE` cần `AccessExclusiveLock` và có thể deadlock với function completion đang
giữ `AccessShareLock`. Quy tắc này thuộc test lifecycle, không thay đổi production completion contract.

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
- FT-057 combined benchmark contract/template trước reliability hardening: [result](./results/04-ft057-bulk-reconciliation-data-plane.md)

`CatalogOperationCoalescingBenchmarkTest` được giữ làm direct canonical baseline FT-054. Nó không chạy relay
và không còn performance gate 10 giây; không dùng nó để qualify FT-059.
