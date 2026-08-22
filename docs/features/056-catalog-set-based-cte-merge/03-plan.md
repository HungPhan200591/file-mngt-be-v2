# FT-056 — BT-09D2 Catalog Set-Based CTE Merge — Plan

Status: `FAILED — V22 qualification failed (25K 39.278 s, 1M timeout)`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` / `catalog_db`.
- Evidence: V19 calibration `2.032 s`, 1M timeout; V20 calibration `2.633 s`, 1M connection failure;
  V21 user-reported chậm hơn V19 và 1M timeout; V22 measured `39.278 s` ở 25K (avg 2.108 ms/page) và 1M timeout. V20–V22 đều thất bại.
- Scope/files: V19/V20/V21/V22 **immutable**;
  report `benchmark/results/03-ft056-set-based-cte-merge.md`, dashboard và README.
- Must preserve: lane fence, claim-per-page, ingest dedupe, equality/watermark, reducer primary/tags/tombstone,
  unique outbox v2, snapshot-size block, cardinality, `statement_timeout < lease`, Catalog DB ownership.
- Read on demand: [Brief](./01-brief.md), [Design](./02-design.md),
  [V21 finalizer](../../../apps/catalog-service/src/main/resources/db/migration/V21__page_nested_catalog_finalizer.sql),
  [FT-055 ingest](../055-catalog-typed-ingest/03-plan.md), [FT-054 reducer](../054-catalog-operation-coalescing/02-design.md),
  [subject v2](../../contracts/events/media.subject.changed.v2.md), `apps/catalog-service/CONTEXT.md`,
  `$author-backend-tests` và `03-CODING_RULES.md` trước khi sửa Java/test.

## Quyết định V22 (Thất bại)

1. Không tối ưu tiếp global UNLOGGED scratch của V21 và không sửa migration đã apply.
2. Tạo hai projection logged, operation-scoped:
   - `catalog_operation_subject_reduction`: một winner/subject, typed metadata + source-order + trace fields.
   - `catalog_operation_asset_reduction`: một winner/asset locator, typed role/tag/timestamp, metadata cần nếu
     asset được bầu primary và source-order (giữ nguyên null semantics hiện hành).
3. Duy trì projection trong cùng transaction ingest, chỉ từ event vừa insert thành công vào durable stage.
   Raw `catalog_discovery_stage` vẫn là audit/rebuild source; không biến reduction thành nguồn không thể phục hồi.
4. Finalizer đọc typed reduction theo operation/lane/page, bulk canonical write và dựng post-state/snapshot một
   lần/subject. Không copy/delete raw JSONB qua scratch và không parse cùng field nhiều lần.
5. **Kết quả đo thực tế V22**: 25K mất 39.278 s (chậm hơn V19 gấp 20 lần), 1M tiếp tục timeout $\rightarrow$ V22 FAILED.

## Bước triển khai

### P0 — Khóa bottleneck trước schema change

1. Ghi exact V21 run khi có log: `mergeMs`, page count, pageExec median/p95/max, exception root cause và run manifest.
   Thiếu exact log không làm mất kết luận fail, nhưng cấm dùng số suy đoán trong dashboard.
2. Bổ sung phase timing cho acquisition, stage/scratch read, subject, asset/tag, primary/metadata,
   snapshot/outbox và checkpoint. Thu `temp_bytes`, buffer read/hit, WAL, lock wait, scratch live/dead rows.
3. Chạy `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON)` trên calibration và 250K trong database
   disposable/rollback-only; không chạy trên production và không bọc timed 1M bằng EXPLAIN. Nếu evidence bác bỏ
   scratch/JSONB churn là bottleneck chính, dừng trước P1 và sửa Design/Plan.

### P1 — Durable typed reduction

4. Migration V22 tạo hai reduction table, PK/index theo `(operation_id, lane_id, subject_key[, locator])`,
   completeness/version marker và retention/cleanup theo operation. Không dùng UNLOGGED làm source of truth.
5. Mở rộng typed ingest để upsert reduction sau durable dedupe trong cùng transaction. Winner dùng
   `(sourcePartition, sourceOffset, eventId)`; duplicate, out-of-order và partial slice phải hội tụ.
6. Thêm rebuild set-based từ `catalog_discovery_stage` cho operation cũ hoặc marker/cardinality lệch.
   Finalizer phải fail-fast hoặc rebuild; không silently finalize projection thiếu.
7. Đo D1 trước/sau ở 25K/250K/1M. Nếu reduction làm D1+D2 dự kiến vượt 60 giây, không chuyển sang P2.

### P2 — Direct typed canonical merge

8. Migration V22 thay `catalog_finalize_operation_page`: page keys/fence giữ nguyên; đọc reduction trực tiếp;
   bulk insert/update subject, asset/tag, tombstone, primary, metadata/actress; subject mới bỏ before-hash.
9. Dựng canonical post-state và payload outbox một lần/subject. Existing/no-op vẫn so before/after và không tăng
   version; snapshot quá lớn vẫn block operation; checkpoint/outbox/counter vẫn atomic trong transaction fence.
10. Bỏ hot-path use của 8 `catalog_finalize_*` scratch table. Chỉ drop ở migration sau khi rollout/rollback window
    đóng; V22 rollback không phụ thuộc việc khôi phục dữ liệu scratch.

### P3 — Tuning có kiểm soát và qualification

11. Chạy ladder page size `500 → 1.000 → 2.000` và worker `1 → 2 → 4`; chọn cấu hình nhỏ nhất đạt gate,
    không vượt pool, không lock convoy và transaction p95 `< lease / 3`. Claim-per-page không đổi, nên chưa nhập D3.
12. Chạy scale ladder `25K → 250K → 1M`, warm-up rồi ba measured run mỗi điểm. Report median/max, throughput,
    phase breakdown, temp/WAL/buffer và combined D1+D2 nếu cost nằm ở ingest.
13. Chỉ đánh dấu recovery `DONE` khi cả ba run 1M `< 60.000 ms`, không timeout/connection loss/cardinality drift.
    Sau đó mới quyết định có tiếp tục stretch `<= 10 s` trong FT-056 hay chuyển sang D3.

## Kiểm tra

- Correctness: subject mới; existing no-op/change; primary election; tags; tombstone; actress/registry;
  duplicate/out-of-order/retry; rebuild parity; fence loss; cardinality mismatch; oversized snapshot.
- Migration: V19/V20/V21 checksum giữ nguyên; empty DB và DB có operation staged trước V22 đều có đường an toàn.
- Performance: 2.500 subject không regress median V19 qua ba run cùng topology; 100.000 subject/1M event
  dưới 60 giây ở cả ba run; combined D1+D2 cũng dưới 60 giây nếu V22 dời work sang ingest.
- Resource: không temp-file exhaustion, connection loss hay unbounded scratch bloat; statement/transaction p95
  nằm trong lease budget; Hikari không starvation.
- Agent không tự chạy Maven, benchmark, Docker hay migration thật cho đến khi người dùng yêu cầu.

## Rollout và rollback

- Rollout: V22 additive; backfill/rebuild theo operation; bật reduction write trước khi dùng finalizer V22.
- Rollback: migration tiếp theo restore function V21 và tắt reduction dual-write; giữ reduction tables đến khi
  xác nhận không còn operation cần rebuild. Không rewrite V19/V20/V21/V22 đã apply.
- Stored procedure/helper SQL tuân thủ giới hạn 500 dòng/file; helper không mở transaction/round-trip mới.
- Không đổi REST/Kafka schema, service boundary, database ownership hay ADR.

## Tài liệu cần cập nhật

- [x] Brief/Design/Plan ghi V21 fail định tính và V22 recovery candidate.
- [x] V22/V22.1 migration, typed ingest reduction, rebuild path và direct finalizer đã triển khai; chưa chạy migration/test.
- [x] `docs/STATUS.md` route BT-09D2 sang V22 implementation/qualification pending.
- [x] Benchmark report/dashboard/README ghi V21 fail, exact metrics pending.
- [ ] Cập nhật số liệu V21/V22 chỉ từ log thật và run manifest.
- [x] Audit `STATUS.md`; không thêm history DONE vào snapshot.
