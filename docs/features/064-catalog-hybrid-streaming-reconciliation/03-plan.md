# FT-064 — Catalog Hybrid Streaming Reconciliation — Plan

Status: `DONE — FUNCTIONAL_PASS — 1M CAPACITY_FAILED`
Owner: `catalog-service`  
Brief: [01-brief.md](./01-brief.md)  
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: Catalog operation reconciliation data plane; chỉ ghi `catalog_db`.
- Scope: unit reader, Java reducer, COPY staging writer, set-based persistence, finalizer dispatch, V29 migration,
  configuration, UT/IT và combined benchmark 25K.
- Must preserve: subject-key page boundary; completion equality; primary/tag/actress/tombstone semantics; monotonic
  version; unique transactional outbox; snapshot byte limit; lease/fence/retry; final broker ACK.
- External contract: không đổi REST/Kafka payload/topic/partitioning/database ownership.
- Runtime bound: page mặc định 2.500 subject; one DB writer transaction per claimed unit; Java compute fan-out only.
- Read on demand: FT-059 plan, V23/V25 reducer, ADR-006/007 và event contracts chỉ khi invariant tương ứng bị chạm.

## P1 — Java reduction plane

1. Đọc toàn bộ immutable input của claimed unit bằng một ordered query.
2. Group theo subject key; fan-out pure reduction bằng virtual-thread executor.
3. Bầu subject winner và asset winner deterministic theo `(sourcePartition, sourceOffset, eventId)`.
4. Chuẩn hóa collection JSON và role mà không thay đổi business semantics.

## P2 — COPY và set-based persistence

1. COPY reduced subject/asset rows vào temp staging trên transaction connection.
2. Apply canonical subjects/assets/relationships/primary/version bằng set-based SQL.
3. Dựng final snapshot/outbox và checkpoint unit/work subject trong cùng transaction có fence.
4. Empty/mismatch/snapshot-too-large/fence-loss phải rollback rõ ràng.

## P3 — Runtime integration

1. Finalizer dispatch claimed unit sang hybrid reducer.
2. Giữ acquire, retry/block, deadline watchdog và begin-committing gates hiện tại.
3. Đổi page size mặc định `500 → 2500`; không tăng DB writer concurrency mặc định.
4. Thêm telemetry read/group/reduce/COPY/apply để benchmark không chỉ có wall-clock tổng.

## P4 — Verification

- UT: comparator/winner, duplicate locator, deterministic order, empty/single/full page và task failure propagation.
- PostgreSQL IT: exact cardinality, primary election, no-op retry, version/outbox uniqueness, tombstone, fence loss,
  oversized snapshot và page 2.500 subject.
- Regression: completion shard, finalizer, reduction và operation finalization suites.
- Chạy Spotless, compile và `git diff --check`.

## P5 — Benchmark và exit

- Chạy đúng combined benchmark 25K một lần sau correctness gate.
- Giữ page 2.500 nếu PASS correctness/liveness; số latency là evidence, không phải production SLO.
- Chỉ hạ page `2500 → 1250 → 625` khi page 2.500 timeout/OOM hoặc fail correctness/liveness do bound; mỗi lần hạ
  phải ghi nguyên nhân. Không tuning worker count và không chạy 250K/1M.
- `DONE` khi source, migration, UT/IT, 25K evidence và source-of-truth đồng bộ; `BLOCKED` nếu invariant hiện tại
  không thể biểu diễn bằng bounded hybrid transaction.

## Rollback

- Revert application dispatch/config về V23/V59 relational reducer.
- Migration append-only; nếu có index/staging object persistent thì migration kế tiếp drop, không sửa V29 đã apply.
- Không xóa durable input/workset; unit chưa checkpoint được retry bằng legacy reducer.

## Kết quả thực thi

- P1–P3 hoàn tất: full-page reader, virtual-thread subject reduction, transactional COPY, hai set-based persistence
  phase, telemetry, page mặc định 2.500 và hard cap 25.000 input rows đã chạy trong production bean thật.
- Correctness/regression đạt **48/48 PASS** trên PostgreSQL 18; Flyway validate/apply 30 migration tới V29.
- Combined 25K đạt exact 25.000 input, 2.500 subjects và final broker ACK trong `7.696 ms` với một unit.
- Hybrid unit `2.566 ms`: read `170 ms`, Java reduce `34 ms`, COPY `149 ms`, SQL apply `2.203 ms`.
- So với V28 stable `7.765 ms`, wall-clock chỉ giảm `69 ms` (~`0,9%`), nằm trong noise local; feature được giữ vì
  functional/architecture pass, không tuyên bố throughput gain. Không hạ page vì 2.500 không timeout/OOM/fail.
- Lượt 1M chạy sau khi đóng implementation đã đạt exact 1.000.000 input, 100.000 subject và final broker ACK trong
  `224.954 ms` (`4.445 input/s`), vượt target 120 giây `104.954 ms`; không chạy 250K.
- 40 unit có execution sum `123.205 ms`; read `6.228 ms`, Java reduce `395 ms`, COPY `4.955 ms`, SQL apply
  `111.313 ms`. Residual bottleneck giữ tại `TD-023`; không tiếp tục tuning trong FT-064.
- Hai cleanup cuối sau lần đo (thu hẹp transaction chỉ còn COPY/apply/finalize và hard cap 25.000 input rows/page)
  đã qua targeted test nhưng không benchmark lại; vì vậy `7.696 ms` là directional evidence trước cleanup, không phải
  số đo xác nhận chính xác cho revision cuối.
