# 001 Bootstrap platform — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: Platform và boundary dùng chung của năm service.
- Scope/files: root Maven, `apps/*` bootstrap, `platform/*`, `infra/compose`, `.gitignore` và README local.
- Must preserve: Không đụng V1; PostgreSQL tách database/user theo service; chưa tạo business table, topic hoặc cross-service call.
- Read on demand: [Design](./02-design.md), `docs/adr/ADR-001-v2-service-and-data-ownership.md`, `infra/compose/README.md`.

## Bước triển khai

1. Tạo Maven Wrapper, root parent/aggregator POM và module `apps/*`, `platform/*` theo Design.
2. Thêm Spring Boot application tối thiểu cho năm service cùng Actuator health; Worker chỉ mở management port, chưa có business HTTP endpoint.
3. Tạo `platform/event-contracts` chỉ với event envelope/version convention tối thiểu; tạo `test-support` không chứa domain logic.
4. Tạo `infra/compose/compose.yaml`, PostgreSQL init script cho `catalog_db`/`scan_db`/`query_db` và user riêng, `.env.example`, `.gitignore`, README local; pin image version/digest sau khi kiểm tra compatibility.
5. Thiết lập `application.yml`/profile local dùng environment variable, port và service name đã chốt.
6. Bổ sung OpenAPI placeholder chỉ khi Gateway/service thực sự public endpoint ngoài health; không tạo spec business rỗng.
7. Khi handoff, đặt Plan là `DONE` và cập nhật `docs/STATUS.md` sang feature/việc kế tiếp.

## Kiểm tra

- Đã chạy static: XML hợp lệ cho 8 POM, đủ 7 module trong aggregator, đủ package entry cho 5 app, Maven Wrapper pin `3.9.16`, không có image `latest` và `git diff --check` đạt. Compose có `kafka-volume-init` để cấp quyền volume cho UID/GID Kafka `1000`.
- Đã xác minh runtime với JDK 25 của IntelliJ: năm application trả `UP` ở cả liveness/readiness; PostgreSQL tạo đúng `catalog_db`, `scan_db`, `query_db` cùng user riêng. Docker Compose được người dùng xác nhận hoàn tất.
- Static: kiểm tra Maven module graph, file naming, `.gitignore`, compose syntax và không có image `latest`.
- Khi người dùng cho phép: `./mvnw test` hoặc compile, `docker compose config`, rồi khởi động local để gọi health của từng service.
- Kiểm tra service chưa tạo bảng nghiệp vụ, không có business topic và không gọi chéo service/database.

## Rollout và rollback

- P0 không đụng V1, database V1 hay frontend; rollout chỉ là tạo repository/code V2.
- Nếu cần rollback, revert feature commit; không xóa Docker volume có dữ liệu nếu chưa được người dùng yêu cầu.
- Không chạy migration/import trong P0.

## Tài liệu cần cập nhật

- `docs/STATUS.md`.
- `docs/adr/ADR-004-local-port-allocation.md` khi thay đổi phân bổ host port local.
- `apps/<service>/CONTEXT.md` chỉ khi entry path/config owner thực tế khác Design.
- `docs/contracts/` chỉ khi tạo public API hoặc event thật.
