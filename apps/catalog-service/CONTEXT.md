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
- Event target SC-01: `media.subject.changed.v2`; runtime v1 sẽ được thay thẳng ở BT-09D, không dual-publish.
  `media.metadata.changed.v1` không đổi trong BT-09A.
- Data plane global 16-unit của [FT-057](../../docs/features/057-catalog-bulk-reconciliation-data-plane/03-plan.md)
  và reliability [FT-058](../../docs/features/058-catalog-operation-reliability-hardening/03-plan.md) đã fail gate
  1M/120s. [FT-059](../../docs/features/059-catalog-logical-shard-completion/03-plan.md) đã `IMPLEMENTED`
  với targeted verification: logical
  completion shard theo canonical subject key, Scan transactional marker, Catalog shard equality gate và bounded
  page reconciliation; Java vẫn chỉ giữ bounded control plane, không làm 1M-row in-memory reducer.
  Combined Catalog release gate là 1M trong tối đa 120 giây từ first receive tới final broker ack; 30K–40K
  input records/s chỉ là stretch capacity result, không chặn release.
- Asset locator canonical gồm `storageKey + relativePath`; `storageKey` có thể thiếu với asset legacy/manual chưa gắn root.
- Subject materialize `baseCode`, `part`, `studioCode`, `actressNames` và `tagNames` từ discovery v2; snapshot
  `media.subject.changed.v2` phát final full snapshot theo operation cho Query. FT-057 data plane và FT-058
  reliability source đã implement; targeted unit/PostgreSQL/Kafka regression đạt 36/36, gồm 4 reconciliation
  units checkpoint đồng thời không lock-upgrade deadlock; Flyway V24 đã verify trên PostgreSQL 18 Testcontainers.
  Combined 25K hoàn tất trong 4.935 ms (`5.066 input records/s`); combined 1M vượt deadline 120 giây do
  reconciliation units lặp lại statement timeout 20 giây và operation còn `RECONCILING`. FT-058 đã dừng ở
  `FEASIBILITY_FAILED`; FT-059 không có benchmark/qualification trong lần implementation này và không tiếp tục
  tối ưu cùng 16-unit transaction shape.
- Catalog bầu đúng một `PRIMARY_VIDEO`: video đầu tiên thắng khi chưa có primary; video không tag ưu tiên hơn
  video có tag; cùng priority giữ primary hiện tại. Tags được lưu theo video asset và subject `tagNames` phản ánh
  primary đang được bầu. Xóa primary kích hoạt election lại từ các video còn lại.

## Invariants

- Catalog là owner resolve asset locator và xóa canonical asset. Subject không còn asset phải bị xóa và phát `media.subject.deleted.v1` bằng transactional outbox.
- Locator tombstone chặn discovery event cũ đến đảo thứ tự làm asset đã xóa sống lại.

- Subject identity dùng key chuẩn hóa theo region/kind.
- Khi feature có business event, mọi thay đổi publish qua transactional outbox.
- Consumer Kafka idempotent; không ghi projection Query trực tiếp.
- Operation ingest không seal trong transaction ghi stage. Seal coordinator chỉ claim committed progress bằng
  `FOR UPDATE SKIP LOCKED`; operation có total deadline 120 giây và retry unit tối đa ba failure có fence.
- Operation FT-059 chỉ seal logical shard khi `media.approval.shard.completed.v1` và unique input count của shard
  hội tụ; Kafka partition vật lý không phải completion boundary. Global ready vẫn cần exact sum mọi shard.
- Outbox publisher của Catalog và Scan dùng bounded lease claim, publish ngoài transaction và conditional
  update; DLT observer theo dõi `media.file.discovered.v2.DLT`.
- Mọi thay đổi asset phải làm aggregate subject tiến version trước khi enqueue outbox; không được tái sử dụng
  `(subject_id, subject_version)`. Subject tags chỉ còn là compatibility projection của primary.
- Không tự scan filesystem.
- Dùng `platform/observability` cho direct-request correlation MDC; expose Prometheus chỉ trên direct
  service port và không đưa identity/path vào metric label.
- ECS JSON log không được chứa secret hoặc absolute media path; ELK lỗi không được chặn Catalog flow.

## Read when needed

- Event: `docs/contracts/events/`.
- Domain feature: folder `docs/features/<feature-id>/`.
