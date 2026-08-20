# FT-055 — BT-09D1 Catalog Typed Fast Ingest — Plan

Status: `READY`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service`
- Scope/files: operation batch consumer/raw payload carrier, typed ingest row/COPY writer, Java lane hash, stage store, migration/index chỉ khi evidence yêu cầu, focused tests và benchmark evidence.
- Must preserve: `media.file.discovered.v2`, operation watermark semantics, durable dedupe, bounded transaction, Catalog database ownership và D2/D3/D4 boundaries.
- Read on demand: [discovery v2](../../contracts/events/media.file.discovered.v2.md), [approval watermark](../../contracts/events/media.approval.watermark.v1.md), `apps/catalog-service/CONTEXT.md`, FT-054 baseline/telemetry và [SLO owner](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/07-performance-slo-and-benchmarks.md#32-approve-1m--query_db_ready--tổng-p95-budget-30s).

## Bước triển khai

1. Chụp baseline implementation hiện tại: wrapper serialization, JSONB parse/cast, SQL lane hash, COPY cardinality, transaction boundary và offset acknowledgement.
2. Truyền raw payload cùng typed routing projection từ consumer; không serialize lại toàn `StageInput` thành wrapper JSON.
3. Thay temp ingest table/COPY thành typed columns, thêm `subjectLane` do Java tính; durable stage vẫn giữ raw payload JSONB và schema contract hiện tại.
4. Giữ `INSERT ... ON CONFLICT(event_id) DO NOTHING RETURNING`; workset/counter chỉ lấy rows mới trong cùng transaction.
5. Bổ sung golden-vector Java/SQL cho lane hash và focused unit/integration tests cho `0`, `1`, full slice, duplicate, retry, mismatch, COPY cardinality/cancel và rollback.
6. Chạy formatter/static checks khi được phép; chạy calibration 25K rồi qualification 250K → 1M trên manifest cố định.
7. Chỉ chuyển feature sang `DONE` khi correctness pass, 25K/1M gate đạt và evidence reproducible; D1 pass không đồng nghĩa BT-09D hoặc `QUERY_DB_READY` pass.

## Kiểm tra

- `git diff --check`.
- Focused unit/integration tests cho Catalog operation ingest.
- Testcontainers PostgreSQL correctness với duplicate và rollback.
- 25K: `stageSql` median `<= 100 ms`, max `<= 150 ms` sau warm-up và tối thiểu 3 clean runs.
- 1M: D1 ingest `<= 4.000 ms`, `>= 250.000 records/s`, profile `100K subjects × 10 assets`.
- Benchmark ghi records/s, mapping/encoding/COPY/stage timings, heap/pool/WAL và run manifest; không claim production SLO.

## Rollout và rollback

Giữ feature flag/config hiện tại; rollout local trước. Typed temp table chỉ sống trong transaction/session, durable schema không đổi nếu không có migration được chứng minh cần thiết. Nếu correctness hoặc throughput không đạt, rollback về wrapper-JSON ingest path và giữ lại evidence thất bại, không xóa dữ liệu benchmark.

## Tài liệu cần cập nhật

- [x] Route `docs/STATUS.md` và BT-09D break-task sang FT-055.
- [ ] Cập nhật `docs/STATUS.md` sau khi có implementation/evidence.
- [ ] Cập nhật benchmark results và runbook nếu gate đạt.
- [ ] Không đổi contract vì FT-055 chỉ tối ưu Catalog ingest nội bộ.
