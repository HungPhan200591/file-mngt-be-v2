# Catalog Service context

## Scope

Nguồn chuẩn cho `media_subject`, `media_asset`, Actress, Studio, Tag và các liên kết nghiệp vụ.

## Owns

- Database `catalog_db`, Flyway Catalog, outbox và processed-event Catalog.
- Command tạo/sửa subject và metadata.
- API write/read canonical ở mức domain.
- Event `media.subject.changed.v1`, `media.metadata.changed.v1`.

## Invariants

- Subject identity dùng key chuẩn hóa theo region/kind.
- Mọi thay đổi publish qua transactional outbox.
- Consumer Kafka idempotent; không ghi projection Query trực tiếp.
- Không tự scan filesystem.

## Read when needed

- Event: `docs/contracts/events/`.
- Domain feature: folder `docs/features/<feature-id>/`.
