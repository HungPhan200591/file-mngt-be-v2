# FT-056 — BT-09D2 Catalog Set-Based CTE Merge — Plan

Status: `IMPLEMENTED — chờ user chạy IT/benchmark`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` / `catalog_db`
- Scope/files: Flyway `V20` thay body `catalog_finalize_operation_page`; IT parity
  `CatalogOperationFinalizeIT`; benchmark `CatalogOperationMergeBenchmarkTest`; report
  `benchmark/results/03-ft056-set-based-cte-merge.md` + dashboard/README; telemetry chỉ nếu thiếu
  median `pageExec`.
- Must preserve: fence lane, page size/claim-per-page (D3), ingest FT-055, equality/watermark,
  reducer primary/tags/tombstone, unique outbox v2, `statement_timeout` < lease, Catalog DB ownership.
- Read on demand: [Brief](./01-brief.md), [Design](./02-design.md),
  [V19 finalizer](../../../apps/catalog-service/src/main/resources/db/migration/V19__optimize_native_catalog_operation_finalizer.sql),
  [FT-054 reducer D4](../054-catalog-operation-coalescing/02-design.md),
  [subject v2](../../contracts/events/media.subject.changed.v2.md),
  `apps/catalog-service/CONTEXT.md`, `$author-backend-tests`, `03-CODING_RULES.md` trước khi sửa Java.

## Bước triển khai

1. Khóa baseline V19: đọc function hiện tại; thêm IT characterization tối thiểu (subject mới / subject cũ
   không đổi) **trước** khi đổi SQL, để parity có oracle.
2. [x] `V20__set_based_cte_catalog_finalizer.sql`: `CREATE OR REPLACE FUNCTION` cùng signature;
   page keys trong array plpgsql + `WITH ... AS MATERIALIZED`; **cấm** temp DDL trong function. V19 immutable.
3. [x] Bypass hash: `before_hash` chỉ khi subject đã có; subject mới `changed = true`, snapshot một lần.
4. Giữ nguyên khối canonical write set-based (insert subject/asset, tombstone, primary election,
   metadata/actress/tags, registry bump, outbox, workset, lane cursor, cardinality/fence checks).
5. [x] `CatalogOperationFinalizeIT` (`@Tag` không benchmark): golden vector nghiệp vụ liệt kê trong Brief;
   Testcontainers `org.testcontainers.postgresql.PostgreSQLContainer`; reset `TRUNCATE ... CASCADE`
   qua fixture chung; mock data sạch (`Studio_Alpha`, `Artist_Alex`, `CODE-…`).
6. [x] `CatalogOperationMergeBenchmarkTest`: `@Tag("benchmark")`, `@SpringBootTest` bật finalizer thật,
   tắt Kafka consumer/outbox relay. Seed bằng ingest path (không tính giờ) → gán
   `READY_TO_COALESCE` → đo drain workset. Log `pageExec` median/p95/max + wall-clock merge.
   Calibration 2.500 subject; qualification 100.000 subject. **Baseline V19 đã chạy 2026-08-21.**
7. [x] Cập nhật `BENCHMARK_RESULTS.md` / `README.md` sau số đo thật; không ghi target vào cột
   candidate. Report: `results/03-ft056-set-based-cte-merge.md`. V19: 2.500 subject `2.032 s` /
   pageExec avg `106 ms`; 100.000 subject **TIMED OUT > 2 min**. Không pass gate D2.
8. Không sửa `CatalogOperationFinalizer.processLanePage` claim/release. Không đổi page size mặc định
   `500` trừ khi manifest D2 chứng minh và vẫn không biến thành D3.

## Kiểm tra

- `grep` body V20: 0 khớp `CREATE TEMPORARY`, `CREATE INDEX`, `ANALYZE`.
- IT parity pass; Java mới/sửa chạy Spotless; class `*Test`/`*IT` khớp regex linter.
- Benchmark local: 2.500 subject có `pageExec` median; 100.000 subject có wall-clock. Gate
  `< 5 ms/page` và `<= 5 s` chỉ pass khi số đo thật đạt — không tuyên bố trước.
- Heap/pool/`statement_timeout` bounded; JDBC fallback không dùng để claim gate.
- Agent không tự chạy Maven/benchmark/Docker/migration thật cho đến khi người dùng yêu cầu.

## Rollout và rollback

- Rollout: Flyway V20 trên `catalog_db`; `catalog.operation.finalizer-enabled` đã có.
- Rollback: migration tiếp theo restore body V19 (không rewrite V20 đã apply); hoặc tắt finalizer.
- Không ADR: không đổi service boundary, event contract hay ownership.
- Không đổi REST/Kafka schema.

## Tài liệu cần cập nhật

- [x] Brief/Design/Plan FT-056.
- [x] `docs/STATUS.md` — trọng tâm chuyển sang D2; sửa nhầm "Watermark Gate".
- [x] `04-break-task.md` / `08-approve-1m-context.md` / `ref-bt09d` — trỏ FT-056.
- [x] Benchmark dashboard/report V19 baseline.
- Không cập nhật architecture summary, ADR, OpenAPI hay event contract.
