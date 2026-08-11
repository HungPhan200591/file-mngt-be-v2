# Catalog Service context

## Scope

Nguồn chuẩn cho `media_subject`, `media_asset`, Actress, Studio, Tag và các liên kết nghiệp vụ.

## Owns

- Database `catalog_db`, Flyway Catalog, outbox và processed-event Catalog.
- Các bảng master data: `studio`, `studio_code`, `tag`, `actress`, `master_data_registry`, `master_data_import`.
- Command tạo/sửa subject và metadata, CRUD/Import Master Data Registry.
- Internal API `GET /api/v2/master-data/scan-registry` cung cấp snapshot cho `scan-service`.
- Internal read-only API `POST /internal/v2/catalog/scan-existence` phân loại batch candidate theo locator
  canonical và subject identity; implementation FT-034 có Flyway V8, nhưng direct verification còn deferred.
- Event `media.subject.changed.v1`, `media.metadata.changed.v1`.
- Asset locator canonical gồm `storageKey + relativePath`; `storageKey` có thể thiếu với asset legacy/manual chưa gắn root.

## Invariants

- Subject identity dùng key chuẩn hóa theo region/kind.
- Khi feature có business event, mọi thay đổi publish qua transactional outbox.
- Consumer Kafka idempotent; không ghi projection Query trực tiếp.
- Outbox publisher của Catalog và Scan dùng bounded lease claim, publish ngoài transaction và conditional
  update; DLT observer theo dõi cả `media.file.discovered.v1.DLT` và `.v2.DLT`.
- Không tự scan filesystem.
- Dùng `platform/observability` cho direct-request correlation MDC; expose Prometheus chỉ trên direct
  service port và không đưa identity/path vào metric label.
- ECS JSON log không được chứa secret hoặc absolute media path; ELK lỗi không được chặn Catalog flow.

## Read when needed

- Event: `docs/contracts/events/`.
- Domain feature: folder `docs/features/<feature-id>/`.
