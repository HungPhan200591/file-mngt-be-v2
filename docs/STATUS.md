# Trạng thái Backend V2

Updated: 2026-08-01

## Hiện tại

- Phase: Giai đoạn 3 — Feature `006-catalog-subject-changed-outbox` đã READY, chuẩn bị triển khai Catalog producer và observability tối thiểu.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: đã chốt monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Catalog P1: `002-catalog-vertical-slice` đã hoàn tất. Catalog sở hữu migration `media_subject`/`media_asset` và API create/detail/list theo OpenAPI v1.
- E2E HTTP: `003-e2e-http-harness` đã hoàn tất; `.http` là kịch bản chung cho IntelliJ và Agent CLI.
- Scan P2: `004-scan-preview` đã hoàn tất code, Testcontainers và E2E fixture; runtime E2E local đã pass với fixture root.
- Scan P3: `005-scan-approval-outbox` đã DONE: decision API, transactional outbox, Kafka publisher, Catalog consumer idempotent, retry/DLT, Testcontainers và E2E fixture. Runtime `npm run scan:local` đã pass 21 request, xác minh một event chỉ tạo đúng một Catalog subject/asset.
- Catalog P4: `006-catalog-subject-changed-outbox` đã READY: full snapshot event có subject version, transactional outbox và operations API đọc outbox/DLT; chưa triển khai code.

## Việc kế tiếp

1. Triển khai `006-catalog-subject-changed-outbox` theo Plan READY.
2. Sau khi Feature 006 pass runtime E2E, tạo feature Query projection thuộc Giai đoạn 5.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
