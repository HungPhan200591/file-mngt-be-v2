# FT-061 — Catalog Shared Ingest Fence — Plan

Status: `IMPLEMENTED — correctness pass; physical performance failed`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service`; Scan/event-contracts không đổi.
- Scope: V59 ingest fence/query, derived progress refresh, blocked-child propagation, final convergence claim,
  targeted tests và đúng hai benchmark gates.
- Must preserve: FT-059 routing/event contract, data/marker ordering, dedupe, exact cardinality, late-input
  fail-closed, DLT, source-order winner, broker-ack terminal boundary và V57 compatibility.
- Read on demand: [Design](./02-design.md), [ADR-006](../../adr/ADR-006-logical-completion-shards.md),
  [FT-060 evidence](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/07-ft060-bounded-upsert-parallelism.md).

## Bước triển khai production

1. Tách `CatalogOperationIngestStore` thành V57 query hiện hành và V59 shared-fenced path.
2. V59 lock parent rows `FOR SHARE` theo operation ID trong statement riêng; statement sau dùng fresh snapshot để
   validate operation/shard, dedupe/insert và update partition progress.
3. Bỏ V59 per-slice shard/parent counter updates; giữ recount tại marker/seal/completion làm authority refresh.
4. Late input chỉ block child shard. Thêm `propagateBlockedShards` trong completion control plane: parent lock trước,
   fresh child read sau, persist failure detail và terminal timestamp; chạy kể cả không còn ingest traffic.
5. Tách `beginCommittingEligibleOperations` thành bounded parent claim và fresh verify/update trong cùng transaction.
6. Giữ default production consumer concurrency `1` trong rollout đầu; targeted/physical gate dùng đúng `4` workers.
   Chỉ đổi default sau khi correctness và physical gate pass.
7. Chỉ thêm migration sau V26 nếu database function phải thay; không sửa migration đã apply.

## Verification trước performance

- Unit: V57/V59 routing, empty/single/full batch, equal-cardinality validation.
- PostgreSQL IT:
  - marker-before-data, data-before-marker;
  - duplicate/retry không đếm lại;
  - bốn workers ingest đồng thời exact 25K, zero parent exclusive lock wait;
  - deterministic ingest-vs-marker, ingest-vs-seal, ingest-vs-final-commit bằng latch/barrier;
  - late unique không insert; child blocked; parent propagation chạy lại được sau restart;
  - duplicate sau seal no-op;
  - transaction failure rollback input/progress;
  - V57 regression giữ nguyên.
- Physical driver bốn workers phải mô phỏng Kafka ownership: mỗi source partition chỉ thuộc đúng một worker;
  không tạo contention giả bằng cách cho nhiều worker cùng cập nhật một partition-progress row.
- Architecture gate: không lock upgrade/cycle; mọi blocked child có đường về parent terminal.

## Performance gate cố định

Chỉ sau toàn bộ targeted UT/IT pass:

1. 25K x3 với bốn ingest workers: exact cardinality, zero deadlock/lock waiter/sampler failure.
2. Physical 1M x1, phase tuần tự, không scheduler/Kafka/overlap.

Decision:

- `<=90s`: chạy combined 25K x3 rồi combined 1M.
- `>90s` và `<=110s`: đóng FT-061 thành công kỹ thuật; mở đúng một FT-062 cho phase còn lại được evidence xác
  định là bottleneck.
- `>110s`: ghi FT-061 performance failed và chỉ mở FT-062 cho bottleneck đã quan sát; không sửa ingest vòng hai.
- Correctness/liveness fail: rollback FT-059, `NO-GO` productionization và không benchmark.

Không chạy 2/4/8 worker comparison; manifest bốn workers đã khóa từ evidence trước.

## Rollout và rollback

- Drain existing V59 operations trước deploy/rollback để không mixed fence semantics giữa app instances.
- Rollout consumer concurrency `1`; nâng tối đa `4` chỉ sau gate, bounded bởi topic partition/pool hiện hành.
- Rollback application về FT-059 exclusive parent fence. Additive migration nếu có được giữ và vô hiệu hóa bằng
  migration sau, không sửa checksum cũ.
- Không cần topic/event cutover.

## Tài liệu cần cập nhật khi implement

- [x] Implementation record và targeted verification evidence: compile pass; targeted regression 35/35.
- [x] Catalog context/STATUS sau gate.
- [x] Benchmark report: 25K x3 pass; physical 1M vượt 110s và dừng; không chạy combined.
- [x] Không cần sửa contract/ADR: implementation không đổi REST, event hoặc database ownership boundary.

## Implementation record — 2026-08-23

- V59 ingest dùng shared parent fence trong statement riêng; statement ghi sau dùng fresh `READ COMMITTED`
  snapshot. V57 giữ exclusive fence tương thích.
- Progress V59 không còn update parent/shard theo từng slice; seal recount durable input. Child `BLOCKED` được
  coordinator propagate lên parent bằng transaction riêng.
- Final convergence claim tối đa 64 parent bằng `FOR UPDATE SKIP LOCKED`, rồi verify/update ở statement sau.
- Compile/Spotless pass; targeted regression 35/35. Evidence performance:
  [08-ft061-shared-ingest-fence.md](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/08-ft061-shared-ingest-fence.md).
