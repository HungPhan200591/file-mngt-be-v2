# FT-059 — Catalog Logical Shard Completion Contract — Plan

Status: `IMPLEMENTED — targeted contract/UT/IT verified; reliability and scale qualification pending`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`, `catalog-service`, `platform/event-contracts`; Query chỉ là downstream hiện hành.
- Scope/files: additive event/ADR, Scan subject-key shard routing + transactional completion outbox, Catalog
  shard equality ledger + bounded page reconciliation, output relay/global convergence, focused tests/benchmark.
- Must preserve: DB ownership, decision/outbox atomicity, immutable typed input, discovery dedupe, source-order
  winner, primary/tags/tombstone/version semantics, snapshot envelope, fence/reclaim, DLT gate, exact global
  cardinality và final broker-ack boundary.
- Read on demand: [Brief](./01-brief.md), [Design](./02-design.md),
  [shard event contract](../../contracts/events/media.approval.shard.completed.v1.md),
  [ADR-006](../../adr/ADR-006-logical-completion-shards.md),
  [FT-058 evidence](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/05-ft058-reliability-hardening.md).

## Bước triển khai

1. **Shared routing và event contract — P0**
   - Thêm `MediaApprovalShardCompletedV1` và shared `ApprovalCompletionShardRouter` trong
     `platform/event-contracts`; khóa `SUBJECT_KEY_MD5_12_RANGE_V1`, range shard count và golden vectors.
   - Provision topic/DLT rõ ràng; partition key `operationId:completionShardId`; retry/DLT theo policy hiện hành.
   - Giữ nguyên payload/version của ba event active hiện tại.

2. **Scan subject-key completion shards — P0**
   - Khảo sát migration number hiện hành rồi thêm forward-only migration cho operation processing version,
     routing bucket/index và shard manifest metadata; không sửa V25 đã apply.
   - Với processing version mới, route proposal theo canonical subject key bucket, không theo proposal UUID.
     Khởi tạo candidate 64 shard ledgers nhưng giữ worker concurrency bounded độc lập.
   - Tính exact expected count mỗi shard từ cutoff snapshot. Transaction hoàn tất shard phải commit decision,
     discovery outbox, shard checkpoint và shard-completed outbox đúng invariant hiện hành.
   - Parent `APPROVAL_COMMITTED` chỉ sau mọi shard complete và tổng shard count bằng global expected count.

3. **Catalog shard ingest/equality gate — P0**
   - Thêm forward-only Catalog migration sau V24 cho processing version mới, completion shard ledger và page
     checkpoint; V19–V24 checksum không đổi.
   - Consume marker idempotent; validate routing version/count/ID và conflict fail closed.
   - Dedupe discovery trước counter, derive shard từ persisted routing bucket, seal đúng một shard khi
     `received=expected`, manifest có mặt và shard không có unresolved DLT.
   - Global watermark đến sớm vẫn chỉ làm parent manifest; không mở shard thiếu completion marker.

4. **Bounded page reconciliation — P0**
   - Materialize stable subject workset theo sealed shard; candidate page 250–500 subjects, keyset/range durable.
   - Reuse FT-057 relational semantics nhưng transaction chỉ đọc một sealed page; không gọi lại 16-unit SQL
     candidate và không hydrate whole operation trong Java.
   - Lock subject theo stable order; mutation result quyết định changed set; snapshot được aggregate một lần.
   - Commit canonical mutation, unique `media.subject.changed.v2` outbox và fenced page checkpoint atomic.

5. **Continuous relay và global convergence — P0**
   - Catalog relay claim output ngay sau page commit; Query có thể project trong khi shard khác ingest/reconcile.
   - Shard complete khi mọi page checkpoint, exact subject/output count và không failure mở.
   - Parent `CATALOG_COMMITTED` chỉ sau mọi shard complete, exact sums, zero unresolved DLT và mọi operation
     output đã broker-ack rồi durable mark.

6. **Reliability, observability và bounded pressure — P1**
   - Thêm crash/reclaim/fence/retry/deadline cho marker, seal, page transaction và ack-before-mark.
   - Tách `completionShardCount`, Scan worker count, Catalog page workers và relay in-flight; startup validation
     cấm cấu hình làm cạn DB pool/lease budget.
   - Metric/log theo operation/shard/page, skew, page latency, WAL/temp/buffers, lock/pool wait và relay tail;
     không dùng identity/path làm label.

7. **Scale qualification và decision gate — P1**
   - Chạy correctness trước, rồi 25K và 250K calibration để chọn shard count/page size trong candidate range.
   - Đóng băng manifest/config trước ba measured run 1M; durability bình thường, seed/assignment/warm-up ngoài clock.
   - `DONE` khi cả ba 1M run `<= 120s`, exact input/subject/output cardinality, zero unresolved DLT và resource
     bounded. `30K–40K/s` chỉ ghi nhận nếu đo được, không chặn DONE.
   - Nếu vẫn fail, giữ `FEASIBILITY_FAILED` cùng phase evidence; không tăng timeout/worker hoặc claim SLO.

## Kiểm tra

- Contract/unit: golden vectors Java/SQL, shard range validation, serialization/deserialization và conflict rules.
- Scan PostgreSQL: cutoff, zero/non-empty shard, exact counts, transactional marker, retry/lease loss và parent sum.
- Kafka/Catalog: marker-before-data, data-before-marker, duplicate/conflict/late input, DLT và restart/reclaim.
- Catalog PostgreSQL: new/no-op/update, multi-asset same subject, duplicate/reorder, primary/tags/tombstone,
  snapshot-size rollback, page fence và exact global convergence.
- Relay: broker failure, ack-before-mark, replay, final ACK clock và no premature `CATALOG_COMMITTED`.
- Benchmark: 25K → 250K → 1M x3, cùng workload manifest; báo phase/skew/resource evidence, không chỉ elapsed time.

Agent không tự chạy build/test/migration/Docker khi chưa được người dùng cho phép rõ ràng.

## Implementation record — 2026-08-22

- Shared: `ApprovalCompletionShardRouter` và `MediaApprovalShardCompletedV1` khóa protocol
  `SUBJECT_KEY_MD5_12_RANGE_V1` với golden vectors và validation shard count.
- Scan: Flyway `V28` persist processing/partitioning version, routing bucket và durable completion-shard
  ledger; final shard transaction ghi marker vào transactional outbox cùng checkpoint/count exact.
- Catalog: Flyway `V25` thêm completion-shard/page ledger, marker idempotency + equality gate, bounded
  page materialization và global convergence. DLT có `routing_bucket`; DLT unresolved chặn seal shard
  tương ứng trước seal, còn payload không route được hoặc DLT đến sau seal fail-closed ở parent operation.
- Đã chạy targeted UT/IT cho router, Scan checkpoint/outbox, Catalog marker/data ordering, conflict/late
  input, DLT isolation và Kafka DLT topology. Không benchmark nào được chạy trong lần triển khai này.

## Rollout và rollback

- Deploy additive event type/topic/DLT và schema trước; consumer hiểu protocol mới trước khi producer phát marker.
- Processing version được persist lúc accept; operation không đổi version giữa chừng. Drain/cancel operation cũ
  trước cutover trong study environment; không trộn global-only FT-057 với FT-059 shard protocol.
- Feature flag bật cho workload nhỏ, qua correctness và scale ladder rồi mới accept 1M.
- Rollback ngừng accept operation version mới và để worker cùng version hoàn tất hoặc chuyển `BLOCKED` để replay;
  không chuyển operation đang chạy về FT-057. Migration rollback bằng migration kế tiếp, không sửa file đã apply.
- FT-057 fallback chỉ là functional path cho workload nhỏ, không phải release fallback 1M vì đã fail feasibility.

## Tài liệu cần cập nhật

- [x] Tạo Brief/Design/Plan FT-059 với hai sơ đồ As-Is/To-Be.
- [x] Tạo event contract `media.approval.shard.completed.v1` và ADR-006.
- [x] Cập nhật contract index, discovery/global watermark compatibility và BT-09 routing/status.
- [x] Cập nhật SC-01 architecture cùng Scan/Catalog context owner.
- [x] Ghi migration/source/test thực tế của implementation.
- [ ] Ghi benchmark report sau scale ladder được cấp quyền; không ghi số giả định là evidence.
