# FT-057 — Catalog Bulk Reconciliation Data Plane — Plan

Status: `READY`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` / `catalog_db`.
- Scope/files: Catalog operation consumer/stage store, ingest SQL, operation reduction/finalizer/lane stores,
  Catalog outbox relay, additive Flyway V23+, operation benchmarks/results và configuration/telemetry liên quan.
- Must preserve: durable raw stage, input dedupe, equality/DLT gate, source-order winner, primary/tags/tombstone,
  subject version, snapshot-size guard, atomic canonical/outbox/checkpoint, unique final snapshot, fence/reclaim,
  Catalog DB ownership và ba event contracts hiện hành.
- Must replace: reduction upsert trong ingest slice, rebuild/recount trong page loop, claim/release mỗi page,
  runtime hash-scan outbox lane và `allOf` wave barrier.
- Read on demand: [Brief](./01-brief.md), [Design](./02-design.md),
  [FT-056 failed plan](../056-catalog-set-based-cte-merge/03-plan.md),
  [FT-054 reducer semantics](../054-catalog-operation-coalescing/02-design.md),
  [Catalog context](../../../apps/catalog-service/CONTEXT.md), event contracts, `03-CODING_RULES.md` trước Java
  và `$author-backend-tests` trước khi sửa test/benchmark.

## Bước triển khai

1. **Khóa baseline và combined clock**
   - Giữ V19–V22 immutable; ghi current 25K/1M failure boundary vào benchmark report FT-057.
   - Thêm operation timer từ first Catalog receive tới broker ack snapshot/watermark cuối và phase telemetry.
   - Không tối ưu nếu benchmark không báo input count, subject count, output count và exact failed phase.

2. **Tách append-only ingest khỏi reduction**
   - Giữ typed bounded COPY và raw payload/source coordinates cần replay.
   - Sau durable dedupe chỉ cập nhật exact operation counters/workset metadata tối thiểu.
   - Bỏ subject/asset conflict-upsert khỏi mỗi ingest slice; chứng minh duplicate không tăng counter.

3. **Build operation reduction đúng một lần**
   - Migration V23+ bổ sung reduction state/checksum/index cần thiết, không sửa checksum migration cũ.
   - Khi equality gate mở, materialize subject/asset winner set-based từ raw stage theo source-order hiện hành.
   - Build idempotent, restart-safe; không `count(*)` raw stage hoặc rebuild lại trong canonical chunk loop.

4. **Thay page finalizer bằng coarse bulk reconciliation**
   - Tạo bounded shard/checkpoint/fence; một claim drain liên tục nhiều chunk tới empty/deadline.
   - Mỗi chunk materialize winner/result set một lần rồi reuse cho subject, asset/tag/primary, snapshot và outbox.
   - Canonical mutation, outbox và checkpoint cùng transaction; operation-wide completion check chạy ngoài
     chunk hot path hoặc đúng một lần tại terminal transition.

5. **Thay Catalog relay data plane**
   - Persist/backfill `relay_lane_id`; tạo partial pending index theo lane/time/id.
   - Fetch bằng index, giữ bounded sliding in-flight window, refill khi ack về và bulk mark theo fence.
   - Thêm heartbeat/deadline/pressure gate; một ack chậm không chặn toàn bộ batch/lane.
   - Phát `CATALOG_COMMITTED` chỉ sau exact outbox published/marked và DLT gate.

6. **Parity, failure và scale qualification**
   - Chạy correctness IT cho new/no-op/update, duplicate/reorder, election/tags/tombstone, size block và fence.
   - Chạy crash/restart ở reduction, canonical và relay; broker ack-before-mark phải replay không mất effect.
   - Chạy ladder `1K → 5K → 50K → 250K → 1M`, sau đó ba measured 1M run cùng manifest.
   - Chỉ đánh dấu implementation `DONE` khi cả ba run `<= 33.334 ms`, correctness exact và resource bounded.
     `<= 25.000 ms` là stretch, không chặn DONE.

## Kiểm tra

- Static/schema: Flyway V19–V22 checksum không đổi; empty DB và upgrade DB đều apply V23+; index/query plan dùng
  persisted relay lane, không seq/hash-scan pending set ngoài bounded expectation.
- Unit: source-order reducer, relay lane derivation, capacity timer, terminal gate và configuration validation.
- Integration/PostgreSQL: ingest dedupe/counter, one-time reduction checksum, canonical parity, fence rollback,
  retry/rebuild và unique outbox.
- Kafka/Testcontainers: real batch receive, bounded in-flight relay, duplicate/reorder/DLT, broker outage/recovery,
  ack-before-mark và `CATALOG_COMMITTED` ordering.
- Benchmark: production-like durability, representative payload, run manifest đầy đủ; báo combined wall clock,
  phase p50/p95/max, SQL/buffer/temp/WAL, pool/lock, lag/ack, retries và output cardinality.
- SLO qualification: ba run chỉ là implementation gate; cần ít nhất 30/100 observation theo SLO owner trước
  khi claim P95/P99 production.

Agent không tự chạy build/test/migration/Docker khi chưa được người dùng cho phép rõ ràng.

## Rollout và rollback

- Rollout additive: deploy schema/index trước, code path mới mặc định tắt; shadow-build reduction/checksum trên
  operation fixture trước khi bật nhận operation mới.
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
- [ ] Khi code hoàn tất, cập nhật benchmark result/dashboard và distill `STATUS.md` theo evidence thật.
