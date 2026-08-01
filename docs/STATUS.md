# Trạng thái Backend V2

Updated: 2026-08-01

## Hiện tại

- Phase: Giai đoạn 3 — Scan approval/outbox đã hoàn tất, gồm runtime E2E local xuyên Scan → Kafka → Catalog.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: đã chốt monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Catalog P1: `002-catalog-vertical-slice` đã hoàn tất. Catalog sở hữu migration `media_subject`/`media_asset` và API create/detail/list theo OpenAPI v1.
- E2E HTTP: `003-e2e-http-harness` đã hoàn tất; `.http` là kịch bản chung cho IntelliJ và Agent CLI.
- Scan P2: `004-scan-preview` đã hoàn tất code, Testcontainers và E2E fixture; runtime E2E local đã pass với fixture root.
- Scan P3: `005-scan-approval-outbox` đã DONE: decision API, transactional outbox, Kafka publisher, Catalog consumer idempotent, retry/DLT, Testcontainers và E2E fixture. Runtime `npm run scan:local` đã pass 21 request, xác minh một event chỉ tạo đúng một Catalog subject/asset.

## Việc kế tiếp

1. Tạo feature kế tiếp của giai đoạn 3 cho Catalog `media.subject.changed.v1` và khả năng quan sát outbox/DLT tối thiểu.
2. Thêm một scenario E2E cho retry/DLT chỉ khi có cách cô lập Kafka lỗi mà không làm phức tạp local workflow.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
