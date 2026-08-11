# 012 Gallery V2 parity foundation — Plan

Status: IMPLEMENTED — BE runtime verified; FE build blocked by baseline errors
Design: [02-design.md](./02-design.md)

## Execution capsule

- Quyết định ưu tiên: triển khai vertical slice Gallery production đã được người dùng chốt ngày 2026-08-11; bổ sung metadata/query/media parity còn thiếu trước khi nối FE.

- Owner: `file_mngt_FE/gallery-v2`; V2 business read boundary qua Gateway/Query, media delivery trực tiếp qua Nginx theo ADR-005.
- Scope/files: `gallery-v2/` context/entry/core owner, parity reference, module router; không sửa Gallery V1.
- Must preserve: V1 runtime/API/settings không đổi; V2 không import V1 logic; chỉ reuse `asset/` và `utils/` Shared UI; browser gọi Gateway `18100` cho business API và Nginx cho media URL.
- Read on demand: `gallery-v2/CONTEXT_GALLERY_V2.md`, `docs/ui-reference/GALLERY_V2_PARITY.md`, `PROJECT_UI_PATTERN.md`, đúng V2 owner; chỉ mở Gallery V1 owner khi parity mismatch.

## Bước triển khai

1. Catalog giữ metadata semantic từ discovery và phát snapshot additive, không đổi topic/event meaning.
2. Query materialize metadata + locator, filter/paging server-side và resolve `mediaUrl` từ deployment root map.
3. FE tách mock thành API/state/presentation, giữ toolbar/hero/card/inline/detail đã chốt và đầy đủ visible states.
4. Action chưa có mutation contract và technical metadata chưa có pipeline được giữ ngoài scope, không fake capability.
5. Typecheck/Testcontainers/E2E/visible verification chỉ chạy khi người dùng cho phép.

## Kiểm tra

- Source owner dưới 500 dòng, `git diff --check`, static syntax check.
- V2 không import từ `gallery-web/` ngoài reference đọc thủ công.
- Shared UI primitive/token được dùng thay vì palette/control cục bộ.
- Card thay đổi phải kiểm đủ Uniform × Details; V1/V2 URL chạy song song.
- Code inspection hoàn tất 2026-08-11: Gallery không còn fixture hay URL media giả; `git diff --check` sạch.
- Maven `verify -DskipTests` pass cho Query (kèm event-contracts); Catalog compile/pass trong reactor build.
- Restart Catalog/Query bằng artifact mới: Flyway Catalog V10 và Query V5 applied; health `UP`; direct và Gateway
  query page/facet response verified.
- FE typecheck/build đã chạy nhưng bị chặn bởi năm baseline error ngoài Gallery trong Scan/Metadata Admin.

## Rollback

- V2 là entry/route độc lập; ẩn link V2 là rollback đủ, không migration durable frontend state.
