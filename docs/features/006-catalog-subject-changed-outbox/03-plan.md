# 006 Catalog subject changed outbox — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service`/`catalog_db` producer; `query-service` chỉ là consumer dự kiến, chưa code trong feature này.
- Scope/files: Catalog Flyway/entity/repository/application/outbox publisher/DLT observer/operations API, event-contracts, Catalog OpenAPI, integration test và E2E tối thiểu.
- Must preserve: canonical write + outbox cùng transaction; duplicate input là no-op; không cross-database; không thêm workflow status; không route operations API qua Gateway; source dưới 500 dòng.
- Read on demand: [Design](./02-design.md), `apps/catalog-service/CONTEXT.md`, event contract, Catalog OpenAPI, `docs/architecture/03-CODING_RULES.md`; dùng `$find-docs` trước API/config Spring Boot 4, Spring Kafka 4, Micrometer hoặc Testcontainers.

## Bước triển khai

1. Thêm `MediaSubjectChangedV1` vào `platform/event-contracts`; hoàn thiện Catalog OpenAPI operations endpoint trước code controller.
2. Thêm Flyway append-only cho subject version/timestamp, `catalog_outbox_event`, `catalog_dead_letter_event`, unique/index/check constraint cần thiết.
3. Tạo component ghi canonical mutation + full snapshot outbox; dùng cho cả REST create và `CatalogFileDiscoveryService`, bảo đảm duplicate/no-op không tạo event.
4. Cài Catalog outbox publisher batch nhỏ, Kafka adapter và property enable/fixed-delay; publish với key `subjectId`, retry cùng `eventId`.
5. Cài DLT observer raw-record idempotent, không tạo DLT-of-DLT loop; thêm operations query service/controller phân trang.
6. Thêm Micrometer counters/gauges và structured log tối thiểu; chỉ expose endpoint Actuator cần thiết trong local config.
7. Thêm Testcontainers/integration test: REST create + outbox atomic, discovered mutation + processed event + outbox, duplicate no-op, publisher retry/success và DLT duplicate.
8. Bổ sung E2E kiểm tra outbox của subject tạo từ Scan đã được publish; cập nhật evidence, Plan `DONE` và `docs/STATUS.md` sau khi người dùng chạy runtime flow.

## Kiểm tra

- Static: event/OpenAPI hợp lệ, Flyway append-only, source line cap, format và `git diff --check`.
- Khi được phép: test Catalog bằng PostgreSQL/Kafka Testcontainers; xác minh rollback khi outbox insert lỗi và duplicate delivery không nhân bản.
- E2E local: `npm run scan:local`, sau đó operations API trả đúng một published Catalog outbox record cho subject/version vừa tạo.
- Failure: Kafka unavailable vẫn giữ pending outbox; DLT observer nhận lại cùng coordinates không nhân bản; operations API không trả payload/stack trace nhạy cảm.

## Rollout và rollback

- Rollout Catalog migration/code trước Query consumer; topic có thể tồn tại mà chưa có consumer.
- Tắt `catalog.outbox.enabled` để dừng publish nhưng vẫn giữ pending event.
- Rollback code bằng revert; không xóa migration, outbox, DLT record hoặc Kafka topic/volume.

## Tài liệu cần cập nhật

- Đã tạo `docs/contracts/events/media.subject.changed.v1.md` và bổ sung operations contract vào Catalog OpenAPI.
- Khi hoàn tất code: cập nhật `docs/STATUS.md` và evidence thực tế trong Plan. Architecture/ownership đã đúng nên không cần ADR mới.
