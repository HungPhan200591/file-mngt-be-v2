# 002 Catalog vertical slice — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` và contract OpenAPI Catalog.
- Scope/files: Catalog POM/config/source/test, Flyway Catalog, `docs/contracts/openapi/catalog-v1.yaml`, `docs/STATUS.md`.
- Must preserve: `catalog_db` only; V1 untouched; no Kafka/outbox relay/Redis/cross-service call; port theo ADR-004; mỗi source file không quá 500 dòng.
- Read on demand: [Design](./02-design.md), [Catalog context](../../../apps/catalog-service/CONTEXT.md), [OpenAPI contract](../../contracts/openapi/catalog-v1.yaml), [ADR-001](../../adr/ADR-001-v2-service-and-data-ownership.md).

## Bước triển khai

1. Xác minh P0 bằng JDK 25 của IntelliJ: Maven Wrapper, compile/test baseline và `docker compose config`; chỉ sửa bootstrap nếu P0 không đạt.
2. Bổ sung dependency Catalog tối thiểu: web, validation, JPA, Flyway, PostgreSQL driver, test và Testcontainers PostgreSQL; cấu hình datasource qua environment variable với default local theo Compose/ADR-004.
3. Tạo Flyway migration cho `media_subject`, `media_asset`, UUID, audit timestamp, unique constraint/index; không tạo outbox hay business topic.
4. Cài domain/application/adapter theo contract: create transaction, get detail, list pagination/filter; map validation/not-found/conflict sang Problem Details.
5. Viết integration test Testcontainers cho migration, create/detail, identity conflict, asset constraint và pagination/filter.
6. Chạy kiểm tra được cho phép, cập nhật Plan `DONE` và `docs/STATUS.md`; không chạy migration/import V1.

## Kiểm tra

- Static: OpenAPI parse, migration naming/order, package boundary, no cross-service/database import, line cap và whitespace.
- Khi được cho phép: `./mvnw test -pl apps/catalog-service -am`, `docker compose -f infra/compose/compose.yaml config`, sau đó chạy Catalog với PostgreSQL local và gọi ba API contract.
- Kiểm tra `catalog_db` chỉ có object Catalog, duplicate identity là `409`, list không vượt max size và không có Kafka topic/event.

## Rollout và rollback

- Local rollout: start Compose, chạy Catalog migration tự động qua Flyway, sau đó mới gọi API.
- Rollback code: revert feature commit. Không tự xóa `postgres-data`; nếu cần xóa schema/database local phải có yêu cầu rõ ràng.
- Migration V1 không bị thay đổi; chưa có dữ liệu V1 được import.

## Tài liệu cần cập nhật

- `docs/STATUS.md` khi bắt đầu/kết thúc code.
- `docs/contracts/openapi/catalog-v1.yaml` là contract source of truth khi API đổi.
- `apps/catalog-service/CONTEXT.md` chỉ khi ownership/invariant thực tế đổi.
