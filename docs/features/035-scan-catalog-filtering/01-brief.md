# FT-035 — Scan–Catalog filtering

Owner: `scan-service` / `scan_db`; dependency: FT-034 Catalog existence provider.

## Mục tiêu

Sau khi Scan parse changed filesystem candidate, gọi Catalog theo micro-batch tối đa 500 để loại locator
canonical đã tồn tại trước khi ghi proposal. Scan vẫn sở hữu inventory, run và proposal; Catalog chỉ trả
snapshot read-only.

## Scope

- `EXACT_ASSET_EXISTS`: không ghi proposal; tính là skipped.
- `EXISTING_SUBJECT_NEW_ASSET`, `NEW_SUBJECT`, `CONFLICT`: ghi proposal với `catalogExistence` evidence.
- Catalog error, timeout hoặc protocol response sai: fail closed run trước transaction commit của chunk.

## Ngoài scope

- Gateway/FE mapping, retry workflow UI, approval/outbox behavior và thay đổi Catalog API.
- HTTP concurrency tuning, retry tự động và benchmark; các quyết định đó chờ evidence runtime.
