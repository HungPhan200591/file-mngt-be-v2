# 019 Catalog master data registry — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service`/`catalog_db`; `scan-service` chỉ thêm REST client và snapshot-per-run, không truy cập `catalog_db`.
- Scope: Catalog Flyway/entity/repository/service/web/OpenAPI/test; Scan REST client, immutable parser context, run/proposal registry version và E2E HTTP.
- Must preserve: Catalog subject/asset behavior, Scan filesystem read-only, existing event v1, no absolute path, existing root port/ownership, source dưới 500 dòng/file.
- Read on demand: Catalog/Scan context, Catalog migrations/entities, Scan start flow, Scan OpenAPI, `docs/contracts/openapi/` và `tests/e2e/README.md`.

## Bước triển khai

1. Viết `catalog-master-data-v1.yaml`: import dry-run/apply, CRUD/search/page, enable/disable và internal scan-registry snapshot; xác định 400/404/409/503 compatibility.
2. Thêm Catalog Flyway cho master tables, normalized unique/index, registry version và import audit. Seed fixture JSON nằm ở test/resource; không chạy import thật.
3. Cài Catalog domain/application CRUD + import validate-all-then-apply; mỗi mutation tăng registry version atomically.
4. Cài Catalog web adapter và integration test: dry-run không ghi, apply hợp lệ, duplicate conflict theo region, disable, snapshot theo region chỉ trả Studio Code/Tag active và version tăng.
5. Thêm Scan REST client với timeout/config; trước khi persist run lấy snapshot. Persist `registryVersion` ở run và truyền snapshot immutable vào parser.
6. Update Scan OpenAPI response nếu `registryVersion` được expose; thêm test Catalog unavailable trả `503` không tạo run, và test run giữ snapshot version.
7. Bổ sung E2E HTTP fixture: import/CRUD master data → snapshot → start Scan; không assert semantic extraction của FT018.
8. Update FT018 Rulebook/Brief sang `PENDING`, link FT019; không sửa semantic parser hay event v1 trong FT019.

## Acceptance checks

- Flyway schema có đúng owner/index/unique; import dry-run không mutation và apply atomic.
- Scan không tạo run khi registry endpoint lỗi; không query/cross-write database Catalog.
- Snapshot immutable theo run, không absolute path/log sensitive data, source cap và `git diff --check`.
- Khi được phép: `./mvnw test -pl apps/catalog-service,apps/scan-service -am` bằng JDK `corretto-25`; E2E theo `tests/e2e/README.md` sau khi người dùng cho phép chạy service.

## Rollout và rollback

- Rollout: deploy Catalog migration/API trước; seed/import qua dry-run rồi apply có chủ đích; sau đó bật Scan registry client.
- Rollback: Scan có thể tắt registry client bằng config chỉ khi chưa dùng FT018 semantic parser; không xóa master data/import audit. Revert code/migration theo quy trình Flyway, không chạy destructive cleanup.

## Source-of-truth audit

- Sẽ thêm OpenAPI Catalog master data trong implementation. Ownership Catalog/Scan giữ nguyên; không cần ADR vì REST snapshot là dependency trực tiếp, nhỏ và per-run consistency.
