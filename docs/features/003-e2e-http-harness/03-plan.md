# 003 E2E HTTP harness — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: platform test tooling.
- Scope/files: `tests/e2e/`, `.gitignore`, `AGENTS.md`, `docs/features/003-e2e-http-harness/`, `docs/STATUS.md`.
- Must preserve: OpenAPI là contract SSOT; không tạo Postman collection song song; không tự khởi động runtime hay chạy request ghi dữ liệu local.
- Read on demand: [Design](./02-design.md), `tests/e2e/README.md`, contract của API đang kiểm tra.

## Hoàn tất

1. Pin `httpYac` CLI cục bộ và thêm script chạy toàn bộ Catalog scenario.
2. Thêm local environment template, gitignore secret/local override và README vận hành.
3. Thêm E2E mẫu Catalog: lifecycle và validation.
4. Route ngắn trong `AGENTS.md`; cập nhật `docs/STATUS.md`.

## Kiểm tra

- Static: JSON parse, package script, `.http` naming/variables/assertions, gitignore, line cap và whitespace.
- Không chạy E2E local trong feature này vì request create ghi dữ liệu vào `catalog_db` local; người dùng sẽ kiểm tra sau khi service chạy.

## Source-of-truth audit

- Không đổi REST contract, database ownership hay architecture; không cần cập nhật OpenAPI, service context hoặc ADR.
