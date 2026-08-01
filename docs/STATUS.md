# Trạng thái Backend V2

Updated: 2026-08-02

## Hiện tại

- Phase: Giai đoạn 6 — Feature `011-frontend-gateway-cutover` đã DONE: Media Library V2 subject-centric dùng Gateway và media delivery V2 do Media Worker sở hữu. Feature `010-gateway-routing-correlation-id` đã DONE.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Catalog P1 `002-catalog-vertical-slice` đã DONE: migration `media_subject`/`media_asset`, API create/detail/list theo OpenAPI v1.
- E2E HTTP `003-e2e-http-harness` đã DONE: `.http` dùng chung IntelliJ và Agent CLI.
- Scan P2/P3 (`004`, `005`) đã DONE: scan preview, approve idempotent, transactional outbox, Kafka publisher, Catalog consumer, retry/DLT, fixture E2E.
- Catalog P4 `006-catalog-subject-changed-outbox` đã DONE: full snapshot event versioned, outbox, DLT observer, operations API và metrics.
- Query P1/P2/P3 (`007`–`009`) đã DONE: projection versioned, Elasticsearch search/fallback, Redis detail cache + metrics.

## Việc kế tiếp

1. Chạy `npm run media:local` sau khi cấu hình root local để xác minh delivery qua Gateway tới filesystem thực.
2. Làm feature observability cho correlation Kafka/OpenTelemetry và structured log end-to-end sau cutover đầu tiên.
3. Quyết định rollout entry/chuyển link từ Gallery Web sang Media Library V2 sau khi dùng thử.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
