# FT-057 — Catalog Bulk Reconciliation Data Plane — Plan

Status: `IMPLEMENTED — verification deferred`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` / `catalog_db`.
- Scope/files: Catalog operation consumer/typed stage store, partition progress, workset/reconcile-unit stores,
  set-based unit SQL, Catalog outbox relay, additive Flyway V23+, benchmark/configuration/telemetry liên quan.
- Must preserve: durable immutable input stage, input dedupe, equality/DLT gate, source-order winner, primary/tags/tombstone,
  subject version, snapshot-size guard, atomic canonical/outbox/checkpoint, unique final snapshot, fence/reclaim,
  Catalog DB ownership và ba event contracts hiện hành.
- Must replace: reduction upsert trong ingest slice, rebuild/recount trong page loop, claim/release mỗi page,
  correlated before/after snapshot, runtime hash-scan outbox lane và `allOf` wave barrier.
- Engine chốt: Java là control plane; PostgreSQL reconcile set-based trực tiếp từ typed stage theo coarse unit.
  Không triển khai Java reducer đọc stage ra JVM rồi COPY winner trở lại.
- Read on demand: [Brief](./01-brief.md), [Design](./02-design.md),
  [FT-056 failed plan](../056-catalog-set-based-cte-merge/03-plan.md),
  [FT-054 reducer semantics](../054-catalog-operation-coalescing/02-design.md),
  [Catalog context](../../../apps/catalog-service/CONTEXT.md), event contracts, `03-CODING_RULES.md` trước Java
  và `$author-backend-tests` trước khi sửa test/benchmark.

## Bước triển khai

1. **Khóa baseline và combined clock**
   - Giữ V19–V22 immutable; ghi current 25K/1M failure boundary vào benchmark report FT-057.
   - Combined gate đo `resume()` tới final broker ack, bắt đầu ngay trước first receive; durable clock từ immutable
     input đầu tiên persisted (`received_at`) tới `published_at` của final watermark chỉ dùng để bóc phase. Không
     gọi seed/assignment/warm-up là throughput.
   - Không tối ưu nếu benchmark không báo input count, subject count, output count và exact failed phase.

2. **Thay ingest bằng immutable typed stage**
   - Migration V23+ tạo `catalog_operation_discovery_input` chứa đủ typed input contract, source order, trace và
     stable routing bucket; new processing version không persist/parse raw JSONB trong hot path.
   - Bounded COPY + durable event-ID dedupe; chỉ cập nhật `catalog_operation_ingest_partition`, không upsert
     subject/asset reduction, workset hoặc một hot operation counter theo slice.
   - Chứng minh typed row reconstruct được input semantics và duplicate không tăng partition/operation count.

3. **Seal operation và build workset/unit đúng một lần**
   - Equality/DLT gate tổng hợp exact partition progress, seal operation rồi materialize stable subject workset.
   - Persist high-resolution routing buckets và nhóm thành `8–64` coarse units theo measured cardinality;
     mỗi subject chỉ thuộc một unit, unit count là tuning configuration chứ không là business invariant.
   - Build idempotent/restart-safe; unique event mới sau seal làm operation `BLOCKED`. Không `count(*)` stage
     hoặc build lại workset trong unit loop.

4. **Thay page finalizer bằng atomic coarse-unit reconciliation**
   - Một claim/fence xử lý một unit trong bounded transaction; statement/transaction deadline nhỏ hơn lease.
   - Trên pinned connection, materialize source-order winner và relational delta một lần vào temporary tables;
     phân loại new/existing/no-op/change trước mutation, không blind conflict-upsert/delete-reinsert collection.
   - Lock affected subject key theo thứ tự ổn định; set-based merge tombstone, subject, asset/tag, primary,
     metadata/actress và version từ current canonical state.
   - Dùng mutation result để xác định changed subject; aggregate post-state snapshot một lần, reuse cho byte guard,
     outbox và checkpoint. Cấm gọi `catalog_subject_state_json` hoặc before/after JSON hash theo subject.
   - Snapshot quá envelope phải rollback toàn unit rồi fenced-mark `BLOCKED` bằng control transaction riêng;
     không commit canonical state nếu thiếu outbox tương ứng.
   - Canonical mutation, final outbox và unit checkpoint cùng transaction. Completion check chỉ ở terminal path.

5. **Thay Catalog relay data plane**
   - Persist/backfill `relay_lane_id`; tạo partial pending index theo lane/time/id.
   - Fetch bằng index, giữ bounded sliding in-flight window, refill khi ack về và bulk mark theo fence.
   - Thêm heartbeat/deadline/pressure gate; một ack chậm không chặn toàn bộ batch/lane.
   - Phát `CATALOG_COMMITTED` chỉ sau exact outbox published/marked và DLT gate.

6. **Parity, failure và scale qualification**
   - Chạy correctness IT cho new/no-op/update, duplicate/reorder, election/tags/tombstone, size block và fence.
   - Chạy crash/restart ở seal/workset, unit transaction và relay; broker ack-before-mark phải replay không mất effect.
   - Workload benchmark chốt: calibration 25K và qualification 1M; sau calibration chạy ba measured 1M run cùng manifest.
   - Chỉ đánh dấu implementation `DONE` khi cả ba run `<= 33.334 ms`, correctness exact và resource bounded.
     `<= 25.000 ms` là stretch, không chặn DONE.

## Kiểm tra

- Static/schema: Flyway V19–V22 checksum không đổi; empty DB và upgrade DB đều apply V23+; index/query plan dùng
  persisted relay lane, không seq/hash-scan pending set ngoài bounded expectation.
- Unit: source-order reducer, relay lane derivation, capacity timer, terminal gate và configuration validation.
- Integration/PostgreSQL: typed-stage dedupe/partition progress, seal/workset cardinality, relational delta,
  canonical parity, fence rollback, retry/rebuild temporary state và unique outbox.
- Kafka/Testcontainers: real batch receive, bounded in-flight relay, duplicate/reorder/DLT, broker outage/recovery,
  ack-before-mark và `CATALOG_COMMITTED` ordering.
- Benchmark: production-like durability, representative payload, run manifest đầy đủ; báo combined wall clock,
  phase p50/p95/max, SQL/buffer/temp/WAL, pool/lock, lag/ack, retries và output cardinality.
- SLO qualification: ba run chỉ là implementation gate; cần ít nhất 30/100 observation theo SLO owner trước
  khi claim P95/P99 production.

Agent không tự chạy build/test/migration/Docker khi chưa được người dùng cho phép rõ ràng.

## Rollout và rollback

- Rollout additive: deploy schema/index trước, code path mới mặc định tắt; shadow-build typed workset/unit
  cardinality trên operation fixture trước khi bật nhận operation mới.
- Không trộn V22 và FT-057 giữa một operation. Operation ghi `processing_version`; worker chỉ xử lý đúng version.
- Bật candidate cho workload nhỏ, qua ladder rồi mới mở 1M; pressure gate tự dừng claim mới khi resource breach.
- Rollback code: tắt FT-057 trước khi accept operation mới; operation đang chạy hoàn tất cùng version hoặc chuyển
  `BLOCKED` để replay, không giữa chừng quay về V22.
- Rollback schema: không drop column/table/index trong incident. Migration forward-only; cleanup chỉ mở feature
  riêng sau retention/reconciliation evidence.

## Tài liệu cần cập nhật

- [x] Tạo Brief/Design/Plan FT-057 và hai sơ đồ As-Is/To-Be.
- [x] Sửa SLO owner: Catalog tối thiểu 30K, stretch 40K; SLI-03 end-to-end P95 60s/P99 90s.
- [x] Route `STATUS.md`, BT-09 context/break-task, Catalog context và architecture overview sang FT-057.
- [x] Đánh dấu FT-056 failed được supersede cho hướng triển khai tiếp theo; giữ evidence lịch sử immutable.
- [x] Mã nguồn FT-057: V23 typed input/workset/unit/relay lane, Java control plane, relay sliding window, phase diagnostics và combined Kafka/finalizer/relay benchmark đã được cập nhật.
- [x] Correctness IT: `CatalogOperationIngestIT` (7), `CatalogOperationReductionIT` (3), `CatalogOperationFinalizeIT` (9) passed ngày 2026-08-22.
- [ ] Chạy Kafka failure matrix và benchmark 25K/1M; chỉ cập nhật result/dashboard bằng evidence runtime thật.
