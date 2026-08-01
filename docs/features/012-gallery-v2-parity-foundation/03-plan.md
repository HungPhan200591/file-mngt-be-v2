# 012 Gallery V2 parity foundation — Plan

Status: DRAFT
Design: [02-design.md](./02-design.md)

## Execution capsule

- Quyết định ưu tiên: tạm hoãn implementation frontend đến khi Backend V2 đủ data/query/media parity; tài liệu này chỉ giữ thiết kế đã chốt.

- Owner: `file_mngt_FE/gallery-v2`; V2 read boundary qua Gateway, Query và Media Worker.
- Scope/files: `gallery-v2/` context/entry/core owner, parity reference, module router; không sửa Gallery V1.
- Must preserve: V1 runtime/API/settings không đổi; V2 không import V1 logic; chỉ reuse `asset/` và `utils/` Shared UI; browser chỉ gọi Gateway `18100`.
- Read on demand: `gallery-v2/CONTEXT_GALLERY_V2.md`, `docs/ui-reference/GALLERY_V2_PARITY.md`, `PROJECT_UI_PATTERN.md`, đúng V2 owner; chỉ mở Gallery V1 owner khi parity mismatch.

## Bước triển khai

1. Tạo module context, entry/shell độc lập và owner map V2; dùng V2 fixture tạm, không gọi V1 API.
2. Tạo V2 state/domain/API boundary; map Query DTO sang V2 model khi contract đủ.
3. Làm từng vertical slice parity: navbar/filter → grid/card → preview/GIF/player → action/context menu.
4. Với field thiếu, tạo feature Query/Catalog riêng trước khi UI slice phụ thuộc vào nó.
5. Thêm screenshot/checklist parity và E2E Gateway cho slice hoàn tất; chạy dual-run pilot trước cutover.

## Kiểm tra

- Source owner dưới 500 dòng, `git diff --check`, static syntax check.
- V2 không import từ `gallery-web/` ngoài reference đọc thủ công.
- Shared UI primitive/token được dùng thay vì palette/control cục bộ.
- Card thay đổi phải kiểm đủ Uniform × Details; V1/V2 URL chạy song song.

## Rollback

- V2 là entry/route độc lập; ẩn link V2 là rollback đủ, không migration durable frontend state.
