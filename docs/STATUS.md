# Trạng thái Backend V2

Updated: 2026-08-01

## Hiện tại

- Phase: Giai đoạn 2 — Scan preview đang được chuẩn bị.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: đã chốt monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Catalog P1: `002-catalog-vertical-slice` đã hoàn tất. Catalog sở hữu migration `media_subject`/`media_asset` và API create/detail/list theo OpenAPI v1.
- E2E HTTP: `003-e2e-http-harness` đã hoàn tất; `.http` là kịch bản chung cho IntelliJ và Agent CLI.
- Feature active: `004-scan-preview` (Plan READY).

## Việc kế tiếp

1. Triển khai `004-scan-preview` theo Plan READY.
2. Giữ guide local hiện hành khi thay đổi Compose/port/runtime.
3. Sau Scan preview, làm approval + Outbox/Kafka theo phase 3.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
