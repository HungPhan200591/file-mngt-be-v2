# FT-041 — Scan rerun overwrite — Plan

Status: IMPLEMENTED — verification deferred

## Execution capsule

- Owner: Scan API/executor, Catalog discovery semantics, FE V2 Scan.
- Scope: request contract, full reconciliation mode, existence filter, regression tests,
  FE API/action, proposal xóa, Catalog/Query reconciliation và module context.
- Must preserve: normal scan changed-only, review gate, lease/checkpoint, event idempotency,
  Catalog asset locator dedupe và `PRIMARY_VIDEO` tag authority.

## Bước triển khai

1. Thêm `overwriteExisting` additive vào start-preview request.
2. Ở rerun, materialize toàn bộ staging và không skip exact Catalog asset.
3. Thêm test normal scan vẫn skip và rerun tạo proposal lại.
4. Thêm nút FE `Rerun & ghi đè`, gửi mode rõ ràng và cập nhật context.
5. Tạo `DELETE_ASSET` cho inventory mất khỏi root; chỉ phát removal event sau approve.
6. Catalog xóa asset/subject mồ côi; Query xóa projection/cache/search bằng tombstone và outbox.

## Kiểm tra

Chạy `git diff --check`; build/test để người dùng cho phép riêng.
