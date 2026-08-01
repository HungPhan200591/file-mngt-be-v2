# 011 Frontend Gateway cutover — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: frontend `file_mngt_FE`; Media Worker sở hữu content I/O, Catalog sở hữu asset locator, Gateway sở hữu public route và Query sở hữu read model.
- Scope/files: Catalog asset `storageKey`, Media Worker HTTP/root registry, Gateway route, OpenAPI/contracts, module Media Library V2, shared API client/config, tests/E2E và tài liệu owner/runtime.
- Must preserve: Gallery Web và Metadata Library V1 không đổi hành vi; frontend chỉ gọi `18100`; không public path tự do/absolute path; Worker không có database; Query/Catalog/Gateway không đọc file; direct ports chỉ cho diagnostics; không gọi operations; không tự động fallback/retry; source dưới 500 dòng và common-first UI.
- Read on demand: bốn owner context, Catalog/Query/media delivery OpenAPI, Gateway HTTP contract, ADR-001/004, coding rules, `file_mngt_FE` module/UI rules và HLD trong Design.

## Bước triển khai

1. Catalog locator: thêm nullable `storage_key` vào `media_asset`, đổi unique locator thành `subject_id + COALESCE(storage_key, '') + relative_path`; persist `sourceRootKey` từ scan approval event, expose additive `storageKey` trong Catalog request/response và giữ request cũ không có field này tương thích.
2. Media delivery: bật HTTP `18104` cho Media Worker, thêm configured root registry, Catalog client, safe-path resolver và GET/HEAD content có MIME/Range/cache validation.
3. Gateway: route đúng `/api/v2/media/subjects/**` đến Media Worker, giữ canonical correlation ID và streaming response; không public Worker Actuator.
4. Frontend foundation: thêm runtime `gatewayBaseUrl`, V2 API client và DTO mapper/error model; không hard-code direct Query/Worker trong component.
5. Media Library V2: thêm route/entry và màn list/detail subject với search, suggestion, region/type, order, pagination, preview media, loading/empty/error/degraded state.
6. Verification: Catalog migration/consumer test, Worker safe-path/range integration test, Gateway streaming route test, frontend client/UI test và E2E qua `18100`.
7. Rollout/docs: cập nhật owner contexts, module/runtime guide và `docs/STATUS.md`; giữ V1 link để rollback trong giai đoạn đầu.

## Kiểm tra

- Static: format/lint theo frontend repo, `git diff --check`, link/Mermaid review và source dưới 500 dòng.
- Contract: query params/enum/page size khớp `query-v1.yaml`; mọi business URL bắt đầu từ Gateway config.
- Media security: unknown/missing storage key, traversal, symlink/reparse escape và file missing đều không lộ path; valid full/range/head response đúng header/body.
- UI: loading, empty, error, degraded, search race, pagination, direct detail URL và responsive layout.
- Runtime: Gateway + Query đang chạy; browser gọi `18100`, không gọi `18103`; correlation ID xuất hiện trong Network và diagnostic error.
- Regression: Gallery Web và Metadata Library V1 vẫn dùng contract cũ, không đổi setting hoặc card behavior.

## Evidence

- 2026-08-02: `mvnw -pl apps/catalog-service,apps/media-worker,apps/gateway-service -am spotless:apply test` PASS bằng JDK 25; Catalog 3, Gateway 7, Media Worker 2 tests.
- Frontend static PASS: `node --check media-library/runtime-config.js`, `api-client.js`, `app.js`; source owner đều dưới 500 dòng và `git diff --check` sạch.
- E2E runtime scenario thêm `npm run media:local`; cần chạy sau khi người dùng khởi động Gateway/Catalog/Media Worker và scan fixture theo `tests/e2e/README.md`.

## Rollout và rollback

- Rollout additive bằng route/module V2 riêng; chưa thay tab mặc định hoặc xóa link V1.
- Rollback bằng feature entry/config quay về màn V1 hoặc direct Query diagnostics đã cấu hình. Không tự động fallback theo request.
- Không có migration/durable frontend state cần phục hồi.

## Tài liệu cần cập nhật

- Cập nhật `file_mngt_FE/docs/MODULE_NAMES.md`, tạo context module ngắn và route từ `AGENTS.md` nếu cần.
- Khi implementation hoàn tất: cập nhật `docs/STATUS.md`, manual runtime frontend và Plan thành `DONE` cùng evidence.
- Khi implementation hoàn tất, đồng bộ Catalog/media delivery OpenAPI với code; Query OpenAPI không đổi.
