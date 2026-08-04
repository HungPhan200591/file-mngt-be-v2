# 4. Database map chi tiết

Mục tiêu chương này không chỉ là biết có table nào, mà hiểu một file tạo row nào, transaction nào bảo vệ dữ liệu và ID nào đi xuyên service.

> Schema thực tế luôn lấy từ Flyway trong `apps/<service>/src/main/resources/db/migration/`. Manual này diễn giải schema hiện tại và phần FT013 dự kiến, không thay thế migration/contract.

## 1. Database ownership

Local dùng một PostgreSQL instance để nhẹ, nhưng mỗi service có database/user riêng.

```mermaid
flowchart TB
    PG["<font color='white'>PostgreSQL instance</font>"] --> SDB["<font color='white'>scan_db<br/>Scan owns</font>"]
    PG --> CDB["<font color='white'>catalog_db<br/>Catalog owns</font>"]
    PG --> QDB["<font color='white'>query_db<br/>Query owns</font>"]
    WORKER["<font color='white'>Media Worker</font>"] --> NODB["<font color='white'>No database<br/>hiện tại + FT013</font>"]
    GATEWAY["<font color='white'>Gateway</font>"] --> NODB

    style PG fill:#9C27B0,stroke:#fff,stroke-width:2px
    style SDB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style CDB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style QDB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style WORKER fill:#2196F3,stroke:#fff,stroke-width:2px
    style GATEWAY fill:#2196F3,stroke:#fff,stroke-width:2px
    style NODB fill:#4CAF50,stroke:#fff,stroke-width:2px
```

`catalog_user` không đọc `query_db`; `query_user` không đọc `catalog_db`. Đồng bộ đi qua Kafka snapshot, không join chéo database.

## 2. Các ID đi xuyên hệ thống

Một file có nhiều ID vì mỗi ID trả lời một câu hỏi khác nhau.

| ID/version | Nơi tạo | Ý nghĩa | Đi sang đâu? |
| --- | --- | --- | --- |
| `scanRunId` | Scan | Một lần scan một root | Scan API/tables |
| `proposalId` | Scan | Candidate của một file | Decision + discovery event |
| discovery `eventId` | Scan | Identity event approve | Catalog processed-event |
| `subjectId` | Catalog | Media logic canonical | Event, Query, frontend |
| `assetId` | Catalog | File vật lý canonical | Event, Query, Media Delivery/Worker |
| `subjectVersion` | Catalog | Revision của full Subject snapshot | Query projection guard |
| processing request `eventId` | Catalog, FT013 | Một job metadata | Worker completion reference |
| completion `eventId` | Worker, FT013 | Một kết quả processing idempotent | Catalog processed-event |
| `projectionVersion` | Query | Subject version đã áp dụng | Chống snapshot cũ |

Không dùng filename/display title làm foreign key xuyên service. ID UUID ổn định; tên có thể sửa.

## 3. Data lineage của một file mẫu

Giả sử scan file:

```text
rootKey      = fixture-joke-video
relativePath = A - [JOKE-001].mp4
profile      = JOKE_VIDEO
```

Sau khi parse:

```text
region       = JOKE
subjectType  = VIDEO
identityKey  = JOKE-001
assetRole    = PRIMARY_VIDEO
storageKey   = fixture-joke-video
```

```mermaid
flowchart TB
    FILE["<font color='white'>Physical file</font>"] --> RUN["<font color='white'>scan_run<br/>run-01</font>"]
    RUN --> PROP["<font color='white'>scan_proposal<br/>proposal-01</font>"]
    PROP --> DEC["<font color='white'>scan_decision<br/>APPROVE</font>"]
    DEC --> SEVENT["<font color='white'>scan_outbox_event<br/>discovery-event-01</font>"]
    SEVENT --> SUBJECT["<font color='white'>media_subject<br/>subject-01</font>"]
    SUBJECT --> ASSET["<font color='white'>media_asset<br/>asset-01</font>"]
    SUBJECT --> CEVENT["<font color='white'>catalog_outbox_event<br/>subject version 0/1</font>"]
    CEVENT --> QSUB["<font color='white'>query_media_subject<br/>subject-01</font>"]
    QSUB --> QASSET["<font color='white'>query_media_asset<br/>asset-01</font>"]

    style FILE fill:#009688,stroke:#fff,stroke-width:2px
    style RUN fill:#9C27B0,stroke:#fff,stroke-width:2px
    style PROP fill:#9C27B0,stroke:#fff,stroke-width:2px
    style DEC fill:#9C27B0,stroke:#fff,stroke-width:2px
    style SEVENT fill:#E91E63,stroke:#fff,stroke-width:2px
    style SUBJECT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ASSET fill:#9C27B0,stroke:#fff,stroke-width:2px
    style CEVENT fill:#E91E63,stroke:#fff,stroke-width:2px
    style QSUB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style QASSET fill:#9C27B0,stroke:#fff,stroke-width:2px
```

## 4. `scan_db` chi tiết

### Quan hệ table

```mermaid
flowchart TB
    RUN["<font color='white'>scan_run<br/>PK id</font>"] -->|1:N scan_run_id| PROP["<font color='white'>scan_proposal<br/>PK id</font>"]
    RUN -->|1:N scan_run_id| ISSUE["<font color='white'>scan_issue<br/>PK id</font>"]
    PROP -->|1:0..1 proposal_id| DECISION["<font color='white'>scan_decision<br/>PK proposal_id</font>"]
    PROP -->|1:0..1 proposal_id| OUTBOX["<font color='white'>scan_outbox_event<br/>PK id</font>"]

    style RUN fill:#9C27B0,stroke:#fff,stroke-width:2px
    style PROP fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ISSUE fill:#9C27B0,stroke:#fff,stroke-width:2px
    style DECISION fill:#9C27B0,stroke:#fff,stroke-width:2px
    style OUTBOX fill:#9C27B0,stroke:#fff,stroke-width:2px
```

### `scan_run`

| Column | Ý nghĩa |
| --- | --- |
| `id` | `scanRunId` |
| `root_key`, `profile` | Nguồn và parser được dùng |
| `status` | `RUNNING`, `COMPLETED`, `FAILED` |
| `started_at`, `finished_at` | Thời gian lifecycle |
| `scanned_file_count` | Số regular file đã thấy |
| `proposal_count`, `issue_count` | Kết quả parse |
| `last_error` | Lỗi fatal của run nếu có |

Unique partial index đảm bảo mỗi `root_key` chỉ có một run `RUNNING`.

### `scan_proposal`

| Column | Ý nghĩa |
| --- | --- |
| `id`, `scan_run_id` | Candidate và run cha |
| `source_relative_path` | Path tương đối, không phải absolute path |
| `profile` | Parser profile đã dùng |
| `candidate_type` | `VIDEO`, `ASSET` hoặc `ALBUM` |
| `identity_key` | Key parser đề xuất |
| `display_title` | Tên hiển thị candidate |
| `asset_role` | `PRIMARY_VIDEO`, `IMAGE`, `GIF` hoặc null |
| `evidence` | Evidence JSON tối thiểu cho review |

Unique `(scan_run_id, source_relative_path)` ngăn cùng file tạo hai proposal trong một run.

### `scan_issue`

Giữ `source_relative_path`, `code`, `detail` của file không parse được. Issue không tự tạo Catalog data.

### `scan_decision`

- PK chính là `proposal_id`: một proposal chỉ có một decision.
- `APPROVE` bắt buộc có `event_id`; `REJECT` bắt buộc không có event ID.
- Gửi lại cùng decision là idempotent; đổi decision sau khi chốt trả conflict.

### `scan_outbox_event`

| Column | Luồng |
| --- | --- |
| `id` | Event identity |
| `proposal_id` | Proposal nguồn; unique |
| `event_type`, `partition_key`, `payload` | Kafka record cần publish |
| `created_at` | Thời điểm ghi cùng decision |
| `published_at` | Null = còn pending; có giá trị = broker đã ack |
| `attempt_count`, `last_error` | Retry kỹ thuật, không cần enum status |

## 5. Scan transaction và publisher

```mermaid
flowchart TB
    APPROVE["<font color='white'>Approve command</font>"] --> TX["<font color='white'>Transaction S1</font>"]
    TX --> DEC["<font color='white'>INSERT scan_decision</font>"]
    TX --> OUT["<font color='white'>INSERT scan_outbox_event</font>"]
    TX --> COMMIT["<font color='white'>COMMIT together</font>"]
    COMMIT --> PUB["<font color='white'>Publisher polls pending</font>"]
    PUB --> KAFKA["<font color='white'>Kafka ack</font>"]
    KAFKA --> MARK["<font color='white'>UPDATE published_at</font>"]

    style APPROVE fill:#4CAF50,stroke:#fff,stroke-width:2px
    style TX fill:#FF9800,stroke:#fff,stroke-width:2px
    style DEC fill:#9C27B0,stroke:#fff,stroke-width:2px
    style OUT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style COMMIT fill:#4CAF50,stroke:#fff,stroke-width:2px
    style PUB fill:#2196F3,stroke:#fff,stroke-width:2px
    style KAFKA fill:#E91E63,stroke:#fff,stroke-width:2px
    style MARK fill:#9C27B0,stroke:#fff,stroke-width:2px
```

Nếu process chết trước commit: cả decision/outbox đều không có. Nếu chết sau commit nhưng trước publish: outbox vẫn pending. Nếu publish thành công nhưng chết trước `published_at`: event được gửi lại, Catalog dedupe.

## 6. `catalog_db` chi tiết

### Quan hệ table hiện tại

```mermaid
flowchart TB
    SUBJECT["<font color='white'>media_subject<br/>PK id</font>"] -->|1:N subject_id| ASSET["<font color='white'>media_asset<br/>PK id</font>"]
    SUBJECT -->|1:N subject_id| OUTBOX["<font color='white'>catalog_outbox_event<br/>PK id</font>"]
    PROCESSED["<font color='white'>catalog_processed_event<br/>PK event_id</font>"]
    DEAD["<font color='white'>catalog_dead_letter_event<br/>topic/partition/offset</font>"]

    style SUBJECT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ASSET fill:#9C27B0,stroke:#fff,stroke-width:2px
    style OUTBOX fill:#9C27B0,stroke:#fff,stroke-width:2px
    style PROCESSED fill:#9C27B0,stroke:#fff,stroke-width:2px
    style DEAD fill:#9C27B0,stroke:#fff,stroke-width:2px
```

### `media_subject`

| Column | Ý nghĩa |
| --- | --- |
| `id` | Canonical `subjectId` |
| `subject_type` | `VIDEO` hoặc `ALBUM` |
| `region` | `JOKE` hoặc `USE` |
| `identity_key` | Code/basename/folder identity |
| `display_title` | Tên hiển thị, có thể null |
| `created_at`, `updated_at` | Audit thời gian |
| `version` | Optimistic lock và snapshot revision |

Unique `(region, subject_type, identity_key)` làm nhiều file cùng identity hội tụ về một Subject.

### `media_asset`

| Column | Ý nghĩa |
| --- | --- |
| `id` | Canonical `assetId` |
| `subject_id` | Subject cha |
| `role` | `PRIMARY_VIDEO`, `VIDEO`, `IMAGE`, `GIF` |
| `storage_key` | Root logic; nullable cho asset legacy/manual |
| `relative_path` | Path trong root |
| `created_at` | Thời điểm Catalog ghi nhận |

Unique locator trong Subject là `(subject_id, COALESCE(storage_key, ''), relative_path)`. Partial unique index giới hạn một `PRIMARY_VIDEO` mỗi Subject.

### `catalog_processed_event`

Chứa `event_id + processed_at`. Catalog insert row này cùng transaction với business mutation. Nếu event ID đã có, consumer no-op.

### `catalog_outbox_event`

Hiện lưu full `media.subject.changed.v1` snapshot:

- `subject_id`, `subject_version`.
- `event_type`, `partition_key`, JSON `payload`.
- `published_at`, `attempt_count`, `last_error`.

Constraint hiện tại unique `(subject_id, subject_version)`. FT013 phải nới constraint vì cùng mutation cần một Subject snapshot và processing request cho từng Asset.

### `catalog_dead_letter_event`

Lưu record lỗi được quan sát theo `original_topic + original_partition + original_offset`, payload/error detail và thời điểm nhận. Nó phục vụ chẩn đoán/replay, không phải business state của Subject.

## 7. Catalog consumer transaction hiện tại

Khi nhận `media.file.discovered.v1`:

```mermaid
flowchart TB
    EVENT["<font color='white'>Discovery event</font>"] --> CHECK["<font color='white'>Check processed eventId</font>"]
    CHECK -->|duplicate| NOOP["<font color='white'>No-op</font>"]
    CHECK -->|new| TX["<font color='white'>Transaction C1</font>"]
    TX --> UPSERT["<font color='white'>Find/create Subject<br/>add Asset if locator new</font>"]
    TX --> DEDUPE["<font color='white'>INSERT processed_event</font>"]
    TX --> SNAPSHOT["<font color='white'>INSERT subject outbox</font>"]
    UPSERT --> COMMIT["<font color='white'>COMMIT together</font>"]
    DEDUPE --> COMMIT
    SNAPSHOT --> COMMIT

    style EVENT fill:#E91E63,stroke:#fff,stroke-width:2px
    style CHECK fill:#FF9800,stroke:#fff,stroke-width:2px
    style NOOP fill:#4CAF50,stroke:#fff,stroke-width:2px
    style TX fill:#FF9800,stroke:#fff,stroke-width:2px
    style UPSERT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style DEDUPE fill:#9C27B0,stroke:#fff,stroke-width:2px
    style SNAPSHOT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style COMMIT fill:#4CAF50,stroke:#fff,stroke-width:2px
```

Nếu bất kỳ insert/update nào lỗi, transaction rollback toàn bộ; event chưa được đánh dấu processed.

## 8. `query_db` chi tiết

### Quan hệ table

```mermaid
flowchart TB
    SUBJECT["<font color='white'>query_media_subject<br/>PK id</font>"] -->|1:N subject_id| ASSET["<font color='white'>query_media_asset<br/>PK id</font>"]
    SUBJECT -->|1:N subject_id| SEARCH["<font color='white'>query_search_outbox<br/>PK id</font>"]
    PROCESSED["<font color='white'>query_processed_event<br/>PK event_id</font>"]
    SEARCH --> ES["<font color='white'>Elasticsearch<br/>media index</font>"]

    style SUBJECT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ASSET fill:#9C27B0,stroke:#fff,stroke-width:2px
    style SEARCH fill:#9C27B0,stroke:#fff,stroke-width:2px
    style PROCESSED fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ES fill:#009688,stroke:#fff,stroke-width:2px
```

### `query_media_subject`

Giữ cùng `subjectId`, business fields và `projection_version`. `projection_version` phải bằng Subject version mới nhất đã áp dụng, không phải version riêng tự tăng ở Query.

### `query_media_asset`

Giữ cùng `assetId`, `subject_id`, role và relative path để trả read DTO. Đây là snapshot copy; Query không sửa Asset canonical.

### `query_processed_event`

Dedupe theo Kafka `eventId`. Ngoài event ID, Query còn so `subjectVersion`; event khác ID nhưng version cũ cũng là no-op.

### `query_search_outbox`

| Column | Ý nghĩa |
| --- | --- |
| `subject_id`, `projection_version` | Snapshot cần index |
| `created_at`, `next_attempt_at` | Lịch publish/retry |
| `indexed_at` | Null khi chưa index thành công |
| `attempt_count`, `last_error` | Backoff/chẩn đoán |

Unique `(subject_id, projection_version)` tránh index cùng revision nhiều lần về mặt logic.

## 9. Query projection transaction và search/cache

```mermaid
flowchart TB
    EVENT["<font color='white'>Subject snapshot</font>"] --> GUARD["<font color='white'>eventId + version guard</font>"]
    GUARD --> TX["<font color='white'>Transaction Q1</font>"]
    TX --> SUBJECT["<font color='white'>UPSERT query subject</font>"]
    TX --> ASSETS["<font color='white'>Replace asset snapshot</font>"]
    TX --> PROCESSED["<font color='white'>INSERT processed_event</font>"]
    TX --> OUTBOX["<font color='white'>INSERT search outbox</font>"]
    OUTBOX --> ES["<font color='white'>Async index Elasticsearch</font>"]
    TX --> EVICT["<font color='white'>After commit<br/>evict Redis detail</font>"]

    style EVENT fill:#E91E63,stroke:#fff,stroke-width:2px
    style GUARD fill:#FF9800,stroke:#fff,stroke-width:2px
    style TX fill:#FF9800,stroke:#fff,stroke-width:2px
    style SUBJECT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ASSETS fill:#9C27B0,stroke:#fff,stroke-width:2px
    style PROCESSED fill:#9C27B0,stroke:#fff,stroke-width:2px
    style OUTBOX fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ES fill:#009688,stroke:#fff,stroke-width:2px
    style EVICT fill:#009688,stroke:#fff,stroke-width:2px
```

PostgreSQL projection commit không phụ thuộc Elasticsearch/Redis đang sống. Search indexing retry riêng; cache eviction lỗi không rollback canonical projection.

## 10. Vì sao Catalog và Query lưu dữ liệu giống nhau?

| Catalog | Query |
| --- | --- |
| Quyết định dữ liệu đúng là gì | Tổ chức dữ liệu để UI đọc nhanh |
| Nhận command/business event | Nhận full snapshot |
| Có business invariant | Có projection/version guard |
| Source of truth | Rebuildable read model |
| Không phụ thuộc Elasticsearch | Đồng bộ Elasticsearch/Redis |

Nếu UI join trực tiếp Catalog với nhiều bảng/metadata/search ở request time, Catalog vừa làm write model vừa làm read model phức tạp. Query tách chi phí đọc/search khỏi write boundary.

## 11. Redis và Elasticsearch nằm ở đâu trong flow?

| Store | Nguồn | Khi lỗi/mất dữ liệu |
| --- | --- | --- |
| Redis detail cache | DTO từ `query_db`, key có TTL | Miss/error đọc PostgreSQL và cache lại |
| Elasticsearch | `query_search_outbox`; rebuild từ `query_db` | Query fallback degraded; Catalog không mất dữ liệu |

Cả hai đều nằm sau Query projection, không nhận write trực tiếp từ Catalog và không giữ Subject/Asset canonical.

## 12. FT013 thay đổi database flow như thế nào?

### Schema dự kiến

Catalog `media_asset` và Query `query_media_asset` thêm field nullable:

| Field | Ý nghĩa |
| --- | --- |
| `content_length` | Kích thước file byte |
| `media_type` | MIME type |
| `source_last_modified_at` | Mtime nguồn |
| `technical_metadata_version` | Processor/schema version đã áp dụng |
| `technical_metadata_updated_at` | Thời điểm Catalog áp dụng completion |

### Transaction tạo Asset sau FT013

```mermaid
flowchart TB
    NEW["<font color='white'>New Asset with storageKey</font>"] --> TX["<font color='white'>Catalog transaction C2</font>"]
    TX --> ASSET["<font color='white'>INSERT media_asset</font>"]
    TX --> SNAPSHOT["<font color='white'>INSERT subject snapshot outbox</font>"]
    TX --> REQUEST["<font color='white'>INSERT processing request outbox</font>"]
    ASSET --> COMMIT["<font color='white'>COMMIT together</font>"]
    SNAPSHOT --> COMMIT
    REQUEST --> COMMIT

    style NEW fill:#4CAF50,stroke:#fff,stroke-width:2px
    style TX fill:#FF9800,stroke:#fff,stroke-width:2px
    style ASSET fill:#9C27B0,stroke:#fff,stroke-width:2px
    style SNAPSHOT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style REQUEST fill:#9C27B0,stroke:#fff,stroke-width:2px
    style COMMIT fill:#4CAF50,stroke:#fff,stroke-width:2px
```

Asset thiếu `storageKey` vẫn được Catalog lưu theo contract hiện tại nhưng không tạo processing request.

### Worker xử lý mà không có database

Worker consume request, đọc file, publish completion rồi mới hoàn tất listener. Nếu crash, Kafka có thể giao lại; thao tác read-only an toàn và completion identity ổn định.

### Transaction áp dụng completion

```mermaid
flowchart TB
    COMPLETE["<font color='white'>Processing completion</font>"] --> GUARD["<font color='white'>eventId + metadataVersion guard</font>"]
    GUARD -->|duplicate / stale| NOOP["<font color='white'>No-op</font>"]
    GUARD -->|newer| TX["<font color='white'>Catalog transaction C3</font>"]
    TX --> META["<font color='white'>UPDATE media_asset metadata</font>"]
    TX --> PROCESSED["<font color='white'>INSERT processed_event</font>"]
    TX --> SNAPSHOT["<font color='white'>INSERT subject snapshot outbox</font>"]
    META --> COMMIT["<font color='white'>COMMIT together</font>"]
    PROCESSED --> COMMIT
    SNAPSHOT --> COMMIT

    style COMPLETE fill:#E91E63,stroke:#fff,stroke-width:2px
    style GUARD fill:#FF9800,stroke:#fff,stroke-width:2px
    style NOOP fill:#4CAF50,stroke:#fff,stroke-width:2px
    style TX fill:#FF9800,stroke:#fff,stroke-width:2px
    style META fill:#9C27B0,stroke:#fff,stroke-width:2px
    style PROCESSED fill:#9C27B0,stroke:#fff,stroke-width:2px
    style SNAPSHOT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style COMMIT fill:#4CAF50,stroke:#fff,stroke-width:2px
```

## 13. Ví dụ row trước và sau FT013

### Trước FT013 — Catalog

```text
media_subject
  id=subject-01, region=JOKE, type=VIDEO,
  identity_key=JOKE-001, version=0

media_asset
  id=asset-01, subject_id=subject-01,
  role=PRIMARY_VIDEO,
  storage_key=fixture-joke-video,
  relative_path=A - [JOKE-001].mp4
```

### Sau completion — Catalog

```text
media_subject
  id=subject-01, version=1

media_asset
  id=asset-01,
  content_length=4,
  media_type=video/mp4,
  source_last_modified_at=...,
  technical_metadata_version=1
```

### Sau snapshot — Query

```text
query_media_subject
  id=subject-01, projection_version=1

query_media_asset
  id=asset-01,
  content_length=4,
  media_type=video/mp4,
  technical_metadata_version=1
```

Giá trị `4` chỉ là ví dụ fixture nhỏ, không phải video thật.

## 14. Điều chưa có trong database

Các table sau chưa tồn tại và không được coi là thiết kế đã chốt:

- Actress, Studio, Tag, alias canonical.
- Subject relation `FULL_ALBUM_OF`.
- Worker job/artifact state.
- Import batch/checkpoint/reconciliation.
- Thumbnail/GIF/hash derivative locator chuyên biệt.

Chúng cần feature Brief/Design riêng trước khi thêm migration.

## 15. Cách tự lần một lỗi dữ liệu

Theo thứ tự, không query chéo trong application:

1. Có `scan_run` và đúng proposal/issue chưa?
2. Proposal có decision và outbox chưa?
3. Outbox đã có `published_at` hay đang có `last_error`?
4. Catalog đã có processed event, Subject và Asset chưa?
5. Catalog subject outbox đã publish snapshot chưa?
6. Query đã có processed event và projection version tương ứng chưa?
7. Search outbox đã có `indexed_at` chưa?
8. Redis/Elasticsearch chỉ kiểm tra sau khi PostgreSQL projection đúng.
9. Sau FT013: kiểm tra processing request, completion/DLT và metadata version trước khi kiểm tra Query.

Chuỗi này giúp xác định lỗi thuộc producer, Kafka delivery, consumer hay projection thay vì nhìn UI rồi đoán.
