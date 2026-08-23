# FT-063 — Catalog Reconciliation Page Access Paths — Plan

Status: `DONE`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` PostgreSQL reconciliation data plane.
- Scope: V28 additive indexes, bounded page maximum đã là 2.500, targeted reconciliation IT và combined 25K.
- Preserved: V57/V59 fence/retry, exact subject/asset/tag cardinality, primary election, version, snapshot envelope,
  unique operation outbox và final broker ACK.
- Không đổi reducer, REST, Kafka contract, completion contract hoặc database ownership.

## Thay đổi đã triển khai

1. Thêm subject-winner index theo `(operation_id, subject_key, source order)`.
2. Thêm partial asset-winner index theo operation, subject, canonical locator và source order.
3. Giữ nguyên reducer và completion contract; Flyway PostgreSQL apply V28 sạch.

## Verification evidence — 2026-08-23

- `CatalogOperationFinalizeIT,CatalogOperationReductionIT`: **12/12 PASS** trên PostgreSQL 18 Testcontainers;
  Flyway validate/apply đủ 29 migrations tới V28.
- Combined 25K: **PASS**, exact 25.000 input → 2.500 subject, một slice, một shard, một unit và final broker ACK.
- `pipelineToFinalAckMs`: **10.981 → 7.765 ms**; throughput **2.277 → 3.220 records/s**.
- `unitExecTotal`: **5.892 → 2.386 ms**; ingest **834 → 1.010 ms**. Net pipeline giảm **29,3%**, trong khi
  reconciliation unit giảm **59,5%**; đổi lại ingest tăng do duy trì hai index.
- Evidence chi tiết: [10-ft063-reconciliation-page-access-paths.md](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/10-ft063-reconciliation-page-access-paths.md).
- Kết quả chỉ là một local 25K run, không phải repeated-run hoặc production/1M qualification; `TD-023` vẫn mở.

## Rollback

- Nếu deployment đại diện cho thấy write amplification lớn hơn finalizer gain, thêm migration mới để drop hai
  V28 indexes; không sửa migration đã apply.
