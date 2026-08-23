# FT-060 — Catalog Bounded Intra-Phase Parallel Data Plane — Plan

Status: `FEASIBILITY_FAILED — benchmark implemented; không productionize`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` benchmark/data-plane evidence.
- Scope/files: sequential physical benchmark fixture, routing-range SQL, bounded executor, resource report,
  benchmark dashboard và current status.
- Must preserve: FT-059 contract/cardinality, DB ownership, source-order winner, primary/tags/actress semantics,
  full snapshot outbox, durability mặc định, zero cross-phase overlap và maximum four DB writers.
- Read on demand: [Brief](./01-brief.md), [Design](./02-design.md),
  [FT-059 physical evidence](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/06-ft059-sequential-physical-feasibility.md).

## Bước triển khai

1. Thêm `routing_bucket` vào benchmark subject/asset reduction scratch và validate range coverage.
2. Tách bulk upsert thành `bulkUpsertRange(startInclusive, endExclusive)`; shared master-data sync chạy một lần
   sau fan-in để tránh hot-row contention.
3. Refactor physical benchmark nhận upsert worker count `1/2/4`; ingest và mọi phase khác giữ serial, chỉ bắt
   đầu phase kế tiếp sau barrier. Gate đầu đã bác bỏ parallel production ingest do parent-row lock.
4. Thêm correctness gate 25K x3 cho hai và bốn workers, exact cardinality và zero pending outbox.
5. Chạy candidate 2 workers; chỉ chạy 4 workers sau khi 2 workers không deadlock/lock wait. So sánh physical 1M
   với baseline `171.871 ms` và mục tiêu `<= 90s`.
6. Ghi report/dashboard/status. Không sửa production runtime nếu chưa có candidate đạt gate.

## Kiểm tra

- Test compile/format/diff.
- 25K x3 cho upsert worker `2`, sau đó upsert worker `4` nếu candidate 2 hợp lệ.
- Physical 1M cho upsert worker `2`, sau đó upsert worker `4` nếu cần/được gate cho phép.
- Exact input/subject/asset/outbox, zero pending, zero deadlock/lock waiter, sampler failure bằng zero.

Người dùng đã cấp quyền chạy benchmark candidate 25K và 1M trong task này; không chạy combined 250K/1M.

## Rollout và rollback

- Candidate chỉ tồn tại trong benchmark; rollback bằng bỏ fixture/test FT-060, không migration/data rollback.
- Nếu không candidate nào đạt gate, giữ FT-059 stable mode và ghi `FEASIBILITY_FAILED` với evidence.
- Nếu candidate đạt gate, mở bước productionization riêng; không copy benchmark-only SQL thẳng vào migration.

## Tài liệu cần cập nhật

- [x] Brief/Design/Plan FT-060.
- [x] Benchmark report và dashboard sau measured runs.
- [x] `docs/STATUS.md`, Catalog context và Plan status sau decision.

## Kết quả chốt

- 2 upsert workers: 25K x3 pass; 1M `145.586 ms`, zero deadlock/lock waiter, fail 120s.
- 4 upsert workers: 25K x3 pass; 1M `271.389 ms`, upsert scale âm tới `161.737 ms`.
- Parallel production ingest bị gate bác bỏ do cùng parent operation `FOR UPDATE` ở mỗi slice.
- Giữ stable mode; bước tiếp theo là thiết kế immutable ingest + progress fan-in, không tăng writer tiếp.
