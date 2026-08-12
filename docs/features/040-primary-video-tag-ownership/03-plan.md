# FT-040 — Primary video tag ownership — Plan

Status: IMPLEMENTED — verification deferred

## Execution capsule

- Owner: `catalog-service`.
- Scope: `MediaSubjectEntity`, `CatalogFileDiscoveryService`, Catalog integration test,
  `media.file.discovered.v2` contract documentation.
- Must preserve: asset deduplication, processed-event idempotency, subject outbox,
  metadata behavior ngoài `tagNames`.

## Bước triển khai

1. Chỉ truyền quyền cập nhật tag vào domain khi event là `PRIMARY_VIDEO`.
2. Thêm regression test cho hai thứ tự primary/asset phụ và kiểm tra asset phụ không
   thể tự tạo tag cấp subject.
3. Cập nhật contract và source-of-truth rulebook; không chạy migration.

## Kiểm tra

Chạy `git diff --check`; test/build runtime để người dùng chủ động yêu cầu ở bước riêng.
