# FT-034 — Catalog batch existence API — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` / `catalog_db`; `scan-service` chỉ là consumer contract tương lai ở BT-05.
- Scope/files: OpenAPI đã chốt; Catalog Flyway locator index; internal web adapter DTO/controller/error;
  application classifier; set-based persistence lookup; metrics; Catalog integration tests; owner context
  và status khi implementation hoàn tất.
- Must preserve: không gọi từ Scan trong FT-034; không đọc/ghi `scan_db`; không mutation/outbox trong
  endpoint; event v1/v2 và public Catalog API không đổi; legacy `storage_key IS NULL` vẫn hợp lệ; source
  Java tối đa 500 dòng/file.
- Read on demand: `apps/catalog-service/CONTEXT.md`, `docs/architecture/03-CODING_RULES.md`,
  [contract](../../contracts/openapi/catalog-scan-existence-v1.yaml), Catalog media entities/migrations,
  `MediaSubjectRepository`, `CatalogFileDiscoveryService`, SC-01
  [cross-service deduplication](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/03-cross-service-deduplication.md).

## Bước triển khai

1. Khảo sát lại owner/package Catalog và đọc coding rules trước khi sửa Java; kiểm tra migration version
   mới nhất rồi thêm Flyway unique partial index global locator. Migration phải fail khi có duplicate,
   không tự cleanup/import/reset data.
2. Tạo DTO/validation và internal controller đúng OpenAPI: batch `1..500`, unique `clientRef`, logical
   `storageKey`, relative path, enum/domain hợp lệ; map validation thành `400 ProblemDetail`.
3. Tạo application classifier read-only với thứ tự locator → subject → primary-role guard; preserve một
   result mỗi `clientRef` và không đưa policy vào controller.
4. Tạo persistence adapter set-based cho locator và subject identity, select bounded projection cần thiết;
   không N+1 và không load collection asset ngoài primary-role evidence cần dùng.
5. Chạy classification trong một read-only transaction isolation `REPEATABLE_READ`; map database
   unavailable thành `503`, không fallback thành `NEW_SUBJECT` và không ghi subject/asset/outbox.
6. Bổ sung metric/log aggregate không có high-cardinality label hoặc full payload; xác nhận endpoint chỉ
   dùng direct Catalog port và Gateway routing không đổi.
7. Thêm Catalog integration tests cho bốn status, matched IDs/conflict code, batch boundary 1/500/501,
   duplicate `clientRef`, legacy null locator, role/subject/primary conflict, retry read-only và query-count
   guard chống N+1.
8. Tự audit/refactor Java theo coding rules và cap 500 dòng. Khi code/evidence hoàn tất, cập nhật
   `apps/catalog-service/CONTEXT.md`, chuyển Plan `DONE`, cập nhật SC-01 evidence/summary và distill
   `docs/STATUS.md`; chưa mở BT-05 trong cùng implementation.

## Kiểm tra

- Review contract ↔ DTO/controller/classification theo từng field/status/error; response không thiếu hoặc
  duplicate `clientRef`.
- Review migration/index: locator non-null unique toàn Catalog, locator null legacy vẫn được index cũ bảo
  vệ trong subject, không có cross-database access.
- Integration test với fixture Catalog chứng minh đủ decision table và endpoint không mutation/outbox.
- Query evidence chứng minh số database round-trip bounded theo request, không tăng tuyến tính theo số
  item.
- Kiểm tra source cap, `git diff --check` và source-of-truth links.
- Chỉ khi người dùng cho phép chạy test: từ root dùng Maven wrapper với IntelliJ Project SDK
  `corretto-25` cho module `apps/catalog-service` và dependency cần thiết; không tự khởi động service,
  Docker Compose hay migration thật.

## Rollout và rollback

- Rollout: audit duplicate locator non-null → deploy Catalog migration/index → deploy internal endpoint →
  direct integration/smoke test. FT-034 chưa có consumer nên không cần coordinated Scan rollout.
- Nếu migration fail do duplicate, dừng rollout và sửa dữ liệu/fixture có chủ đích; không bypass unique
  constraint hoặc tự xóa record.
- Rollback application: ngừng expose endpoint; unique index có thể giữ vì nó củng cố invariant canonical.
  Không viết down migration/drop index tự động trong rollback vận hành.
- BT-05 chỉ được mở sau khi contract/direct tests ổn định; client phải có timeout/retry/fail-closed riêng.

## Tài liệu cần cập nhật

- Đã chốt trong bước tạo feature: Brief, Design, Plan,
  [OpenAPI](../../contracts/openapi/catalog-scan-existence-v1.yaml), OpenAPI README, SC-01 BT-04 owner và
  `docs/STATUS.md`.
- Khi implementation hoàn tất: cập nhật Catalog context để ghi endpoint đang tồn tại, thêm evidence vào
  SC-01 summary/question chain nếu đã kiểm chứng và distill trạng thái `DONE`.
- Không cần ADR: database ownership và synchronous direct REST boundary Scan–Catalog không đổi.
- Không cập nhật architecture tổng thể, event contract hay `docs/TECHNICAL_DEBT.md`: feature chưa đổi các
  source of truth đó và không trả debt đang đăng ký.
