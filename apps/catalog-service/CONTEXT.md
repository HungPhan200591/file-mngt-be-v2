# Catalog Service context

## Scope

Nguồn chuẩn cho `media_subject`, `media_asset`, Actress, Studio, Tag và các liên kết nghiệp vụ.

## Owns

- Database `catalog_db`, Flyway Catalog, outbox và processed-event Catalog.
- Command tạo/sửa subject và metadata.
- API write/read canonical ở mức domain.
- Event `media.subject.changed.v1`, `media.metadata.changed.v1`.
- Asset locator canonical gồm `storageKey + relativePath`; `storageKey` có thể thiếu với asset legacy/manual chưa gắn root.

## Invariants

- Subject identity dùng key chuẩn hóa theo region/kind.
- Khi feature có business event, mọi thay đổi publish qua transactional outbox.
- Consumer Kafka idempotent; không ghi projection Query trực tiếp.
- Không tự scan filesystem.
- Dùng `platform/observability` cho direct-request correlation MDC; expose Prometheus chỉ trên direct
  service port và không đưa identity/path vào metric label.
- ECS JSON log không được chứa secret hoặc absolute media path; ELK lỗi không được chặn Catalog flow.

## Read when needed

- Event: `docs/contracts/events/`.
- Domain feature: folder `docs/features/<feature-id>/`.
