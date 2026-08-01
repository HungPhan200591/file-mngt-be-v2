# Trạng thái Backend V2

Updated: 2026-08-02

## Hiện tại

- Phase: Phase 6 chỉ mới có delivery foundation; quay lại hoàn thiện backend theo thứ tự Phase 4 → Phase 7 → Phase 8 trước Gallery V2.
- Active feature: `013-media-worker-processing-foundation` đã `READY`; phạm vi là technical metadata + vòng Kafka completion, chưa gồm thumbnail/GIF/hash.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Catalog P1 `002-catalog-vertical-slice` đã DONE: migration `media_subject`/`media_asset`, API create/detail/list theo OpenAPI v1.
- E2E HTTP `003-e2e-http-harness` đã DONE: `.http` dùng chung IntelliJ và Agent CLI.
- Scan P2/P3 (`004`, `005`) đã DONE: scan preview, approve idempotent, transactional outbox, Kafka publisher, Catalog consumer, retry/DLT, fixture E2E.
- Catalog P4 `006-catalog-subject-changed-outbox` đã DONE: full snapshot event versioned, outbox, DLT observer, operations API và metrics.
- Query P1/P2/P3 (`007`–`009`) đã DONE: projection versioned, Elasticsearch search/fallback, Redis detail cache + metrics.
- Phase 6 foundation (`010`, `011`) đã DONE: Gateway/correlation, Media Delivery qua Gateway và Media Library smoke UI.
- Gallery V2 (`012`) đã chốt thiết kế nhưng `DRAFT`; không triển khai frontend trước backend parity.

## Gap trước khi cutover frontend

- **Phase 4:** Media Worker chưa có processing pipeline: technical metadata, thumbnail, GIF, hash, completion event và Catalog update.
- **Phase 7:** chưa có importer/backfill V1: inventory root, dry-run, batch idempotent, checkpoint và reconciliation.
- **Phase 8:** observability mới có nền metrics/cache; chưa có trace Kafka/OpenTelemetry và ELK end-to-end.

## Việc kế tiếp

1. Triển khai **FT013 — Media Worker processing foundation** theo Plan `READY`, hoàn tất technical metadata + completion event; chưa làm frontend.
2. Tạo feature **Phase 7 — Import/backfill V1 foundation**: inventory root, canonical locator, dry-run và reconciliation.
3. Sau hai phase trên, bổ sung Query read model theo data mới và làm **Phase 8 observability** cho luồng backfill/processing.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
