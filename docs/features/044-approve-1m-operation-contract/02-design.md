# FT-044 — Design: SC-01 BT-09A Operation Contract & Watermark

## 1. High Level Design

Diagram trả lời câu hỏi: operation đi từ lúc được accept tới `QUERY_DB_READY` bằng completion manifest
cardinality thấp như thế nào?

```mermaid
flowchart TB
    CLIENT(["<font color='white'>Client<br/>Approve 1M</font>"])
    SCAN["<font color='white'>Scan Service<br/>ACCEPTED + bounded commit</font>"]
    SDB[("<font color='white'>scan_db<br/>operation status</font>")]
    DATA_1{{"<font color='white'>media.file.discovered.v2</font>"}}
    CATALOG["<font color='white'>Catalog Service<br/>batch + coalesce</font>"]
    CDB[("<font color='white'>catalog_db<br/>canonical + counters</font>")]
    DATA_2{{"<font color='white'>media.subject.changed.v2</font>"}}
    QUERY["<font color='white'>Query Service<br/>bulk projection</font>"]
    QDB[("<font color='white'>query_db<br/>projection + watermark</font>")]
    CONTROL{{"<font color='white'>media.approval.watermark.v1</font>"}}
    READY(["<font color='white'>QUERY_DB_READY<br/>SLO end</font>"])
    SEARCH>"<font color='white'>Elasticsearch<br/>async lane</font>"]

    CLIENT -->|"POST approve"| SCAN
    SCAN -->|"Commit ACCEPTED O(1)"| SDB
    SCAN -->|"HTTP 202 + operationId"| CLIENT
    SCAN -->|"Decision + outbox chunks"| DATA_1
    DATA_1 --> CATALOG
    CATALOG --> CDB
    CATALOG -->|"One final snapshot per subject"| DATA_2
    DATA_2 --> QUERY
    QUERY --> QDB
    QUERY --> READY
    QUERY -.-> SEARCH
    SCAN -.-> CONTROL
    CATALOG -.-> CONTROL
    QUERY -.-> CONTROL
    CONTROL -.->|"Status projection"| SDB

    style CLIENT fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style SCAN fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style CATALOG fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style QUERY fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style SDB fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style CDB fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style QDB fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style DATA_1 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style DATA_2 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style CONTROL fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style READY fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style SEARCH fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
```

## 2. Lifecycle và ownership

| Stage | Durable owner | Contract |
| --- | --- | --- |
| `ACCEPTED` | Scan | Operation row commit O(1), trả HTTP `202`; đây là SLO start. |
| `APPROVAL_COMMITTED` | Scan | `scanCommittedRecordCount = expectedRecordCount`; decision + outbox atomic theo chunk. |
| `CATALOG_COMMITTED` | Catalog | Đủ unique discovery event; final snapshot count được chốt sau coalesce. |
| `QUERY_DB_READY` | Query | Đủ unique subject snapshot, Query DB + watermark + search outbox commit; SLO end. |
| `SEARCH_READY` | Query search worker | Elasticsearch đã index đủ; không chặn Query DB. |

Scan là owner tracking API và giữ projection trạng thái trong `scan_db`. Catalog/Query không ghi ngược
`scan_db`; mỗi stage phát transactional control event. `queryDbReadyAt` là timestamp Query commit, không phải
timestamp Scan consume control event.

## 3. Completion protocol tối ưu SLO

- `media.approval.watermark.v1` dùng key `operationId`, chỉ có O(stage) event cho mỗi operation.
- Progress counter flush theo bounded batch, không update per-record.
- Scan manifest mang `expectedRecordCount`; Catalog chờ unique processed count bằng expected count.
- Catalog phát đúng một v2 snapshot cho mỗi affected subject, sau đó manifest mang `expectedSubjectCount`.
- Query dedupe theo `eventId`, đếm unique `(operationId, subjectId)` và chỉ ready khi count bằng expected.
- Manifest/data nằm ở topic khác nhau có thể đến lệch thứ tự; equality gate giải quyết hội tụ, không giả định
  global Kafka ordering.

## 4. Event v2 và idempotency

`media.subject.changed.v2` là full final snapshot và là runtime target duy nhất. Payload bắt buộc có
`operationId`, `batchId`, `subjectId`, `subjectVersion`; tracing nằm ở optional Kafka headers.

Query target upsert:

```sql
ON CONFLICT (subject_id) DO UPDATE
SET ...
WHERE EXCLUDED.subject_version > query_subject.projection_version;
```

Duplicate cùng version là no-op; không dùng `>=`. Study project thay thẳng v1 bằng v2 ở BT-09D/E,
không dual-publish và reset local topic/projection trước qualification.

## 5. Cache, DLT và terminal state

- Query không `DEL` cache theo từng subject. Projection commit tạo cache generation mới; Redis switch O(1).
- Redis lỗi thì cache bypass/fallback Query PostgreSQL, không chặn `QUERY_DB_READY`.
- Poison event đi DLT để batch khác tiếp tục nhưng operation chuyển `BLOCKED`; unresolved DLT cấm ready.
- Replay hội tụ bằng event dedupe + version guard. Unrecoverable operation chuyển `FAILED`; user cancellation
  chuyển `CANCELLED`.

## 6. Tracking API

- `POST /api/v2/scans/runs/{scanRunId}/approve` → `202 OperationAccepted`.
- `GET /api/v2/scans/operations/{operationId}/status` → durable status projection.

Ví dụ hợp SLO:

```json
{
  "operationId": "019ffb4f-2222-7aaa-8bbb-222222222222",
  "status": "QUERY_DB_READY",
  "expectedRecordCount": 1000000,
  "scanCommittedRecordCount": 1000000,
  "catalogProcessedRecordCount": 1000000,
  "expectedSubjectCount": 148321,
  "queryProjectedSubjectCount": 148321,
  "unresolvedDltCount": 0,
  "acceptedAt": "2026-08-15T22:00:00Z",
  "queryDbReadyAt": "2026-08-15T22:00:25Z"
}
```
