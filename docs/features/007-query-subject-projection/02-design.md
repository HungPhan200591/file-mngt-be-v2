# 007 Query subject projection — Design

Owner: `query-service`; producer contract: [media.subject.changed.v1.md](../../contracts/events/media.subject.changed.v1.md)

## High Level Design

Diagram trả lời câu hỏi: Query Service tiêu thụ event snapshot từ Kafka để xây dựng Read Model PostgreSQL và phục vụ API query phân trang như thế nào?

```mermaid
flowchart TB
    KAFKA["Kafka Event Bus<br/>(media.subject.changed.v1)"] --> CONSUMER["MediaSubjectChanged<br/>Consumer"]

    subgraph QUERY_SERVICE["Query Service Boundary (query_db)"]
        CONSUMER -->|Dedupe event_id| DEDUPE[("query_processed_event")]
        CONSUMER -->|Compare subjectVersion & Reconcile| PROJ_SVC["QueryProjectionService"]
        PROJ_SVC -->|Upsert Read Model| READ_DB[("query_media_subject & query_media_asset")]
    end

    CONSUMER -->|Processing Error after Retries| DLT["Kafka DLT Topic<br/>(media.subject.changed.v1.DLT)"]

    CLIENT["Gallery Web / Client"] --> REST["Query REST Controller<br/>(GET /api/v2/query/subjects)"]
    REST -->|Two-step Paged Fetch| READ_DB

    style KAFKA fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style CONSUMER fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style DEDUPE fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style PROJ_SVC fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style READ_DB fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style DLT fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style CLIENT fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style REST fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
```

## Quyết định

- `query_media_subject`/`query_media_asset` là snapshot projection PostgreSQL, không phải canonical data.
- Consumer dedupe `eventId`; subject chưa tồn tại luôn nhận snapshot đầu tiên kể cả `subjectVersion=0`. Event version thấp hoặc bằng projection hiện có là no-op nhưng vẫn được đánh dấu processed.
- Snapshot mới reconcile assets theo `assetId` trong cùng transaction: cập nhật asset còn tồn tại, thêm mới và xóa asset vắng mặt. DLT theo `<topic>.DLT`, retry 2 lần với backoff 1 giây.
- REST chỉ đọc projection; `search` là contains-ignore-case trên identity/title. `order=CREATED_AT|TITLE`. List phân trang subject trước rồi fetch assets theo tập ID của trang để không phân trang trên collection fetch.

## Contract

- Kafka input: `media.subject.changed.v1`, key `subjectId`, at-least-once.
- REST owner: `docs/contracts/openapi/query-v1.yaml`.
- Eventual consistency biểu diễn bằng `projectionVersion` và `projectedAt`; không thêm status.

## Rollback

Revert code có thể dừng consumer; không xóa projection/processed records. Rebuild/backfill là feature riêng.
