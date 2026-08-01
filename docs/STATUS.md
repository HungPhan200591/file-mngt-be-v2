# Trạng thái Backend V2

Updated: 2026-08-01

## Hiện tại

- Phase: Giai đoạn 3 — Catalog subject outbox đã hoàn tất, gồm Testcontainers và runtime E2E local.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: đã chốt monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Catalog P1: `002-catalog-vertical-slice` đã hoàn tất. Catalog sở hữu migration `media_subject`/`media_asset` và API create/detail/list theo OpenAPI v1.
- E2E HTTP: `003-e2e-http-harness` đã hoàn tất; `.http` là kịch bản chung cho IntelliJ và Agent CLI.
- Scan P2: `004-scan-preview` đã hoàn tất code, Testcontainers và E2E fixture; runtime E2E local đã pass với fixture root.
- Scan P3: `005-scan-approval-outbox` đã DONE: decision API, transactional outbox, Kafka publisher, Catalog consumer idempotent, retry/DLT, Testcontainers và E2E fixture. Runtime `npm run scan:local` đã pass 21 request, xác minh một event chỉ tạo đúng một Catalog subject/asset.
- Catalog P4: `006-catalog-subject-changed-outbox` đã DONE: full snapshot event có subject version, transactional outbox, DLT observer, operations API đọc outbox/DLT và metrics; Catalog Testcontainers cùng runtime E2E local đã pass.

## Việc kế tiếp

1. Tạo và triển khai feature Query projection thuộc Giai đoạn 5.
2. Sau Query projection, thêm Elasticsearch index và Query search/filter API theo ADR-003.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
