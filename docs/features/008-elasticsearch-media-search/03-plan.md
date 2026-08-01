# 008 Elasticsearch media search — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `query-service`, `query_db` và Elasticsearch index do Query sở hữu.
- Scope/files: Query dependency/config, Flyway search outbox, search adapter/application/web, index mapping, Compose profile, Query OpenAPI, Testcontainers, E2E và runbook.
- Must preserve: Catalog canonical; PostgreSQL Query vẫn hoạt động khi Elasticsearch hỏng; event v1 không đổi; port theo ADR-004; version pin; source dưới 500 dòng; không thêm business status.
- Read on demand: Query context, coding rules, ADR-003/004, Query OpenAPI, Feature 007 projection và official Elasticsearch Java Client docs.

## Bước triển khai

1. Hạ tầng: thêm Elasticsearch `9.2.5` vào Compose profile `search`, healthcheck/volume/port `18113`; bổ sung Query client config và local example, không dùng `latest`.
2. Index owner: thêm mapping `media-subject-v1`, alias constants và adapter official Java Client cho create/bulk/search/suggest/alias swap.
3. Reliable indexing: thêm Flyway `query_search_outbox`; khi Feature 007 áp dụng snapshot mới thì enqueue cùng transaction; scheduled publisher bulk-index idempotent và ghi metrics.
4. Search API: route text search qua Elasticsearch, hydrate PostgreSQL theo hit order, thêm relevance/fuzzy/filter/pagination, response degraded/fallback và autocomplete.
5. Rebuild: synchronous operation tạo candidate index, bulk snapshot, catch-up, alias swap nguyên tử; lỗi giữ alias cũ và cleanup candidate.
6. Contract/operation: cập nhật Query OpenAPI, Compose README và tài liệu cá nhân khởi động/search/rebuild; cập nhật `docs/STATUS.md` khi có evidence.

## Kiểm tra

- Static: `spotless:apply`, `git diff --check`, OpenAPI/mapping parse, source dưới 500 dòng và audit version/port.
- Query Testcontainers PostgreSQL + Elasticsearch: fuzzy search, suggestion, hydrate order, rebuild nhiều batch và alias swap đã pass. Query integration regression xác minh fallback PostgreSQL khi search tắt và pagination PostgreSQL không bị giới hạn 10.000.
- Regression: chạy toàn bộ Query/Feature 007 integration test để detail/list PostgreSQL không đổi.
- Runtime: người dùng bật Compose profile `search`, chạy Query rồi `npm run scan:local`; E2E chấp nhận fallback khi profile không bật.

## Rollout và rollback

- Rollout local: bật profile `search`, chạy Query, gọi rebuild một lần rồi kiểm tra E2E. Search chưa có alias tự fallback PostgreSQL.
- Rollback code không xóa PostgreSQL projection/outbox. Đổi alias về physical index trước hoặc tắt profile `search`; API tiếp tục fallback.

## Tài liệu cần cập nhật

- `docs/contracts/openapi/query-v1.yaml`, `docs/STATUS.md`, `infra/compose/README.md` và manual vận hành Elasticsearch/rebuild.
- Không cần ADR mới nếu implementation giữ đúng ADR-003/004; chỉ cập nhật ADR khi đổi owner, port hoặc vai trò canonical.
