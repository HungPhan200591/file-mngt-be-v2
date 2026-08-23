# FT-062 — Catalog Subject Target Mapping — Plan

Status: `DONE — FEASIBILITY_FAILED; no production change`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` physical benchmark/data-plane evidence.
- Scope: `CatalogPhysicalFeasibilitySql`, bounded executor assertions, FT-062 benchmark methods/report và
  current status.
- Must preserve: FT-061 fence, exact cardinality, source winner, primary/tag/actress semantics, phase barrier,
  four ingest workers, two upsert workers và zero lock/deadlock.
- Read on demand: [Design](./02-design.md),
  [FT-061 evidence](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/08-ft061-shared-ingest-fence.md), production V23 target mapping.

## Bước triển khai

1. Tạo UNLOGGED `benchmark_catalog_subject_target(subject_key, subject_id, routing_bucket)` và reset cùng
   reduction tables.
2. Bỏ `subject_id` khỏi reduction. Gộp subject upsert + canonical mapping vào data-modifying CTE `RETURNING`.
3. Chuyển asset/tag/actress/subject-tag joins sang target table; giữ range ownership.
4. Thêm assertion target cardinality và method benchmark FT-062 riêng; giữ FT-060/061 method tái hiện evidence.
5. Compile/format, chạy targeted fixture test nếu có, 25K x3 rồi physical 1M x1. Không chạy combined nếu >90s.
6. Ghi report, Plan, Status và Catalog context. Không sửa production migration trong feature này.

## Rollback

- Candidate chỉ chạm benchmark fixture; rollback bằng bỏ target scratch/method FT-062.
- FT-061 production correctness baseline không đổi.

## Gate

- Correctness/liveness fail: `NO-GO`, không chạy 1M.
- 25K x3 pass: chạy physical 1M đúng một lần.
- Physical `<=90s`: cho phép combined 25K x3 rồi combined 1M ở task tiếp theo.
- Physical `>90s`: đóng FT-062 với evidence, không tối ưu vòng hai trong cùng feature.

## Kết quả — 2026-08-23

- Compile/Spotless pass; existing-subject conflict-path pass.
- 25K x3 pass: `3.470 / 2.915 / 2.715 ms`, exact cardinality, zero deadlock/lock waiter/sampler failure.
- Physical 1M vượt 90 giây khi hai backends vẫn active trong subject-upsert CTE; run dừng theo gate.
- Không chạy combined, không sửa production V23 và không cần contract/ADR/migration update.
- Evidence: [09-ft062-subject-target-mapping.md](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/09-ft062-subject-target-mapping.md).
