# Trạng thái Backend V2

Updated: 2026-08-02

## Hiện tại

- Phase: Giai đoạn 6 — Feature `012-gallery-v2-parity-foundation` READY: Gallery V2 tách core khỏi V1, dùng V1 làm visual reference và Gateway làm read boundary.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Catalog P1 `002-catalog-vertical-slice` đã DONE: migration `media_subject`/`media_asset`, API create/detail/list theo OpenAPI v1.
- E2E HTTP `003-e2e-http-harness` đã DONE: `.http` dùng chung IntelliJ và Agent CLI.
- Scan P2/P3 (`004`, `005`) đã DONE: scan preview, approve idempotent, transactional outbox, Kafka publisher, Catalog consumer, retry/DLT, fixture E2E.
- Catalog P4 `006-catalog-subject-changed-outbox` đã DONE: full snapshot event versioned, outbox, DLT observer, operations API và metrics.
- Query P1/P2/P3 (`007`–`009`) đã DONE: projection versioned, Elasticsearch search/fallback, Redis detail cache + metrics.

## Việc kế tiếp

1. Triển khai Gallery V2 foundation theo FT012 với fixture độc lập và shared UI primitives.
2. Bổ sung Query read model theo field matrix của card V2, theo từng vertical slice.
3. Làm observability correlation Kafka/OpenTelemetry sau khi Gallery V2 có pilot flow.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
