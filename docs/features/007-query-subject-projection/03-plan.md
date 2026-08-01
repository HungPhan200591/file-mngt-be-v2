# 007 Query subject projection — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `query-service`/`query_db`; Catalog chỉ là event producer.
- Scope: Query Flyway/entity/repository/consumer/API/error config/test, Query OpenAPI và E2E extension.
- Must preserve: no cross-database access, idempotent/versioned projection, no ES/Redis, source dưới 500 dòng.
- Read on demand: Query context, event contract, Query OpenAPI, coding rules.

## Bước triển khai

1. Tạo Query schema projection/processed event và event consumer.
2. Áp dụng snapshot theo version, DLT/retry và REST list/detail.
3. Thêm Testcontainers integration và Scan-to-Query E2E polling.

## Kiểm tra

- `./mvnw.cmd spotless:apply`: pass ngày 2026-08-01.
- `./mvnw.cmd test -pl apps/query-service -am` bằng IntelliJ JDK 25: pass, `QueryIntegrationTest` chạy 1 test, 0 failure/error/skipped. Test bao phủ version 0, duplicate/stale event, reconcile asset giữ nguyên ID, list/detail/filter/validation và serializer DLT.
- `git diff --check` và giới hạn 500 dòng: pass ngày 2026-08-01.
- Runtime E2E trước đợt review đã pass 24/24. Cần restart `query-service` rồi chạy lại `npm run scan:local` để xác nhận binary mới trong local runtime; Agent không tự restart service.
