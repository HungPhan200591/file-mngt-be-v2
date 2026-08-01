# 005 Scan approval outbox — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`/`scan_db` producer; `catalog-service`/`catalog_db` consumer; Scan OpenAPI và event contract.
- Scope/files: Flyway và source/test hai service, Kafka/outbox config, `docs/contracts/openapi/scan-v1.yaml`, `docs/contracts/events/media.file.discovered.v1.md`, `tests/e2e/scan/` và `docs/STATUS.md`.
- Must preserve: Scan preview read-only filesystem; Catalog là canonical write owner; không cross-database access; no file mutation; port ADR-004; source dưới 500 dòng/file; ít status.
- Read on demand: [Design](./02-design.md), `apps/scan-service/CONTEXT.md`, `apps/catalog-service/CONTEXT.md`, Scan/Catalog OpenAPI, event contract, `docs/architecture/03-CODING_RULES.md`.

## Bước triển khai

1. Cập nhật Scan OpenAPI và thêm event contract v1 trước code; thêm dependency/config Kafka tối thiểu cho Scan/Catalog.
2. Thêm Flyway Scan: `scan_decision`, `scan_outbox_event`, unique proposal decision và index outbox pending. Thêm Flyway Catalog: `catalog_processed_event` và unique asset idempotency cần thiết.
3. Cài Scan application use case quyết định proposal, kiểm ownership run/proposal và cùng transaction tạo decision + outbox khi approve.
4. Cài Scan outbox publisher batch nhỏ; publish thành công cập nhật `published_at`, lỗi tăng attempt và lưu `last_error`.
5. Cài Catalog Kafka consumer idempotent, map event sang subject/asset và persist dedupe + upsert trong một transaction.
6. Thêm Testcontainers integration: same/conflicting decision, outbox atomicity, publish retry, Catalog duplicate event và asset-before-video. Thêm Scan E2E approve/reject fixture.
7. Chạy kiểm tra được phép; cập nhật Plan `DONE` và `docs/STATUS.md` với evidence thực tế.

## Evidence hoàn tất

- `./mvnw spotless:apply` và `./mvnw test -pl apps/catalog-service,apps/scan-service -am` bằng JDK 25: `BUILD SUCCESS` ngày 2026-08-01.
- Catalog: 2 integration test pass cho REST, duplicate event và asset-before-video. Scan: 2 integration test approval/reject và 2 unit test publisher pass.
- `git diff --check` sạch, không có wildcard import trong source đã chạm và mọi file thay đổi đều dưới 500 dòng.
- E2E CLI/IntelliJ đã có approve idempotent và reject. Runtime E2E với Kafka/Catalog cần chạy sau khi người dùng restart hai service để Flyway áp dụng migration mới nhất.
- Runtime local đã áp dụng V2 trước khi constraint bổ sung được thêm. V2 được giữ nguyên checksum `596466511`; cột `event_id` và constraint bổ sung chuyển sang V3 theo nguyên tắc migration append-only.

## Kiểm tra

- Static: OpenAPI/event contract hợp lệ, migration ownership/naming, source line cap, `mvn validate` format check.
- Khi được phép: test riêng Scan/Catalog bằng Testcontainers; test Kafka integration với broker test container hoặc embedded equivalent được chốt trong implementation.
- E2E local: user khởi động Compose và service, approve fixture proposal; xác minh outbox published và Catalog có subject/asset đúng một lần.
- Failure: Kafka unavailable không mất outbox; consumer nhận lại cùng `eventId` không nhân bản dữ liệu; reject không tạo event.

## Rollout và rollback

- Rollout local sau khi Kafka Compose healthy. Bắt đầu bằng fixture root, không dùng media thật.
- Rollback code bằng revert feature commit. Không xóa outbox/processed-event record hoặc database volume khi chưa có yêu cầu rõ ràng; publisher có thể dừng để giữ pending event an toàn.

## Source-of-truth audit

- Cập nhật Scan OpenAPI, thêm event contract. Architecture/service ownership giữ nguyên nên không cần ADR mới.
