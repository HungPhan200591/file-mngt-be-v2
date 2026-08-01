# Trạng thái Backend V2

Updated: 2026-08-01

## Hiện tại

- Phase: Giai đoạn 5 — Feature `007-query-subject-projection` đã hoàn tất; Feature `008-elasticsearch-media-search` có Plan `READY`, chưa triển khai code.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: đã chốt monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Catalog P1: `002-catalog-vertical-slice` đã hoàn tất. Catalog sở hữu migration `media_subject`/`media_asset` và API create/detail/list theo OpenAPI v1.
- E2E HTTP: `003-e2e-http-harness` đã hoàn tất; `.http` là kịch bản chung cho IntelliJ và Agent CLI.
- Scan P2: `004-scan-preview` đã hoàn tất code, Testcontainers và E2E fixture; runtime E2E local đã pass với fixture root.
- Scan P3: `005-scan-approval-outbox` đã DONE: decision API, transactional outbox, Kafka publisher, Catalog consumer idempotent, retry/DLT, Testcontainers và E2E fixture. Runtime `npm run scan:local` đã pass 21 request, xác minh một event chỉ tạo đúng một Catalog subject/asset.
- Catalog P4: `006-catalog-subject-changed-outbox` đã DONE: full snapshot event có subject version, transactional outbox, DLT observer, operations API đọc outbox/DLT và metrics; Catalog Testcontainers cùng runtime E2E local đã pass.
- Query P1: `007-query-subject-projection` đã DONE: PostgreSQL projection versioned từ version 0, asset snapshot reconcile theo ID, Kafka consumer retry/DLT, Query read API phân trang hai bước và Scan-to-Query E2E extension. Query Testcontainers và runtime E2E 24/24 sau review đều pass.
- Query P2: `008-elasticsearch-media-search` đã có Brief/Design/Plan `READY`; chưa sửa code. Scope gồm reliable search outbox, fuzzy/autocomplete, fallback PostgreSQL và rebuild alias.

## Việc kế tiếp

1. Khi người dùng duyệt triển khai, implement trọn Feature `008-elasticsearch-media-search` theo Plan `READY`.
2. Sau search có evidence, đánh giá Redis cache dựa trên latency/hot query thực tế thay vì cache mặc định.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
