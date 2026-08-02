# Trạng thái Backend V2

Updated: 2026-08-02

## Hiện tại

- Phase: tạm kéo Phase 8 baseline lên trước để quan sát, debug và đo hệ thống hiện có; sau đó mới quay lại Phase 4/FT013.
- Active feature: `014-observability-performance-foundation` đã `READY`; phạm vi là Prometheus/Grafana và ECS log vào ELK. k6 được để feature sau.
- Deferred feature: `013-media-worker-processing-foundation` vẫn `READY` nhưng chưa triển khai; không tạo thêm Plan status chỉ để biểu diễn tạm dừng.
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
- **Phase 8:** FT014 mới có Plan; chưa có Prometheus/Grafana hoặc ELK end-to-end. OpenTelemetry trace xuyên Kafka và k6 nằm ngoài FT014.

## Việc kế tiếp

1. Triển khai **FT014 — Observability và performance foundation** theo Plan `READY`, theo thứ tự metrics → logs.
2. Dùng dashboard/log để đọc và debug lại các flow 002–011 cho đến khi chủ dự án nắm vững.
3. Khi chủ dự án sẵn sàng, quay lại **FT013 — Media Worker processing foundation**; sau đó mới lập feature Import/backfill V1.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
