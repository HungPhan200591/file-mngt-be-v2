# 003 E2E HTTP harness

Owner: platform test tooling

## Mục tiêu

Tạo một bộ kịch bản E2E duy nhất chạy được trực tiếp trong IntelliJ và không cần GUI khi Agent kiểm tra API local.

## Acceptance criteria

- OpenAPI giữ vai trò contract source of truth; `.http` là source of truth cho E2E scenario.
- Có CLI pin version để Agent chạy cùng file `.http` với IntelliJ.
- Có environment template không chứa secret và local file bị gitignore.
- Có Catalog scenario create/detail/list/conflict và validation làm mẫu.

## Ngoài phạm vi

- Không tạo Postman collection, CI pipeline, API mới, migration hay thay đổi ownership.
- Không chạy service/Compose hoặc E2E local tự động vì scenario create ghi dữ liệu E2E vào Catalog local.
