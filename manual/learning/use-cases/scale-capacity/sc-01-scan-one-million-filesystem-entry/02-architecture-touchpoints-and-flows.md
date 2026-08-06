# SC-01 — Chi tiết các luồng và điểm chạm kiến trúc

> Deep-dive triển khai cho SC-01. `01-deep-dive.md` giữ overview; file này trả lời mỗi điểm chạm làm gì, dữ liệu đi đâu và code theo thứ tự nào.
>
> Đây là target architecture thực dụng cho môi trường study/dev. Có thể reset data khi đổi schema; endpoint/schema mới trong tài liệu là mục tiêu của SC-01, không phải baseline đang chạy.

## 1. Bản đồ 8 điểm chạm

Mục tiêu là cắt một scan lớn thành tám trách nhiệm có ranh giới rõ: tạo job, giữ ownership, tìm file, parse, commit chunk, đọc review, bulk decision và publish outbox. Không đưa Kafka vào giữa file walker và database vì proposal/issue vẫn thuộc Scan Service.

```mermaid
flowchart TD
    FE["<font color='white'>Admin UI<br/>trigger, poll, review</font>"]
    API["<font color='white'>1 - Scan REST API<br/>202 + scanId</font>"]
    LEASE["<font color='white'>2 - Job lease<br/>owner + heartbeat</font>"]
    WALK["<font color='white'>3 - File walker<br/>bounded discovery</font>"]
    PARSER["<font color='white'>4 - Proposal parser<br/>proposal hoặc issue</font>"]
    BATCH["<font color='white'>5 - Chunk writer<br/>checkpoint + counters</font>"]
    DB[("<font color='white'>scan_db</font>")]
    QUERY["<font color='white'>6 - Review API<br/>keyset cursor</font>"]
    BULK["<font color='white'>7 - Bulk job<br/>decision + outbox</font>"]
    PUB["<font color='white'>8 - Outbox publisher<br/>at-least-once</font>"]
    KAFKA[("<font color='white'>media.file.discovered.v2</font>")]

    FE -->|"Tạo scan"| API
    API -->|"Tạo run"| LEASE
    LEASE -->|"Worker được phép chạy"| WALK
    WALK -->|"Path theo chunk"| PARSER
    PARSER -->|"Proposal / issue"| BATCH
    BATCH -->|"Commit local transaction"| DB
    FE -->|"Review cursor"| QUERY
    QUERY -->|"Đọc proposal"| DB
    FE -->|"Approve / reject phạm vi"| BULK
    BULK -->|"Decision + outbox"| DB
    DB -->|"Event chưa publish"| PUB
    PUB -->|"Broker acknowledgement"| KAFKA

    style FE fill:#2196F3,stroke:#fff,stroke-width:2px
    style API fill:#2196F3,stroke:#fff,stroke-width:2px
    style LEASE fill:#FF9800,stroke:#fff,stroke-width:2px
    style WALK fill:#009688,stroke:#fff,stroke-width:2px
    style PARSER fill:#FF9800,stroke:#fff,stroke-width:2px
    style BATCH fill:#FF9800,stroke:#fff,stroke-width:2px
    style DB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style QUERY fill:#2196F3,stroke:#fff,stroke-width:2px
    style BULK fill:#FF9800,stroke:#fff,stroke-width:2px
    style PUB fill:#E91E63,stroke:#fff,stroke-width:2px
    style KAFKA fill:#E91E63,stroke:#fff,stroke-width:2px
```

| Điểm chạm | Owner/code target | Input → output | Quyết định triển khai |
| --- | --- | --- | --- |
| 1 | `ScanController` + `ScanService` | `POST /api/v2/scans/previews {rootKey}` → `202 {scanId}` | Tạo `scan_run`, giao worker nền, không giữ HTTP request. |
| 2 | `ScanLeaseManager` | `scanId + workerId` → granted/lost | Một `rootKey` có một owner active; worker mất lease dừng trước chunk sau. |
| 3 | `ScanWalker` | root + checkpoint boundary → `Path` chunk | `Files.walk()` đọc lazy; queue bounded `1.000` là buffer giữa discovery và DB. |
| 4 | `ScanFileAnalyzer` | `Path` → proposal/issue | Parse thuần theo profile/registry; không giữ state của toàn run. |
| 5 | `ScanChunkWriter` | tối đa 500 item → commit | Ghi `scan_proposal`/`scan_issue`, checkpoint và counter cùng transaction. |
| 6 | `ScanQueryService` | cursor → trang proposal | Cursor `(source_relative_path, id)`, không OFFSET sâu. |
| 7 | `BulkDecisionJob` | snapshot filter + action → progress | Claim 500 proposal, ghi decision/outbox/progress cùng transaction. |
| 8 | `ScanOutboxPublisher` | `scan_outbox_event` → Kafka | Publish sau commit, đánh dấu `published_at` khi broker ACK; Catalog dedupe `eventId`. |

## 2. Luồng tạo job và lease

API hiện tại đã dùng `rootKey`, `scanId` UUID và `202 Accepted`; SC-01 giữ naming này. Target thêm `worker_id`, `lease_until`, `checkpoint_path` và counters tiến độ vào `scan_run`. Vì đây là dev study, migration có thể reset database thay vì phải tương thích dữ liệu cũ.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Admin UI
    participant API as Scan REST API
    participant DB as scan_db
    participant Worker as Scan worker

    Admin->>API: POST /api/v2/scans/previews { rootKey }
    API->>DB: Create scan_run RUNNING + lease owner
    DB-->>API: scanId UUID
    API-->>Admin: 202 Accepted { scanId }
    API->>Worker: Start async job(scanId)
    Worker->>DB: Renew lease before each chunk
```

Nếu `rootKey` đã có run active thì trả `409 Conflict`. Lease timeout ban đầu là 30 giây; `workerId` và điều kiện CAS trong update lease là fencing đơn giản để worker cũ không tiếp tục ghi sau takeover.

## 3. Discovery, parse và chunk commit

Đây là hot path của SC-01. Một producer đọc path từ filesystem; consumer gom tối đa 500 item rồi persist. Queue không làm scan nhanh hơn; nó tách tốc độ disk I/O khỏi DB I/O và giới hạn heap.

```mermaid
flowchart TD
    DISK[("<font color='white'>Filesystem root<br/>1M entries</font>")]
    WALK["<font color='white'>Files.walk()<br/>lazy stream</font>"]
    META["<font color='white'>Đọc metadata<br/>lọc file hợp lệ</font>"]
    QUEUE[("<font color='white'>ArrayBlockingQueue<br/>capacity 1.000</font>")]
    PARSER["<font color='white'>Parse profile/registry<br/>proposal hoặc issue</font>"]
    CHUNK["<font color='white'>Chunk 500<br/>local transaction</font>"]
    DB[("<font color='white'>scan_db<br/>data + checkpoint</font>")]

    DISK -->|"Read path"| WALK
    WALK -->|"Read attributes"| META
    META -->|"queue.put(item)"| QUEUE
    QUEUE -->|"Drain tối đa 500"| PARSER
    PARSER -->|"Entity đã phân loại"| CHUNK
    CHUNK -->|"Commit atomically"| DB
    QUEUE -.->|"Full: walker block"| WALK

    style DISK fill:#009688,stroke:#fff,stroke-width:2px
    style WALK fill:#009688,stroke:#fff,stroke-width:2px
    style META fill:#009688,stroke:#fff,stroke-width:2px
    style QUEUE fill:#E91E63,stroke:#fff,stroke-width:2px
    style PARSER fill:#FF9800,stroke:#fff,stroke-width:2px
    style CHUNK fill:#FF9800,stroke:#fff,stroke-width:2px
    style DB fill:#9C27B0,stroke:#fff,stroke-width:2px
```

Transaction của một chunk:

1. Insert proposal vào `scan_proposal` và issue vào `scan_issue`.
2. Update `checkpoint_path`, `scanned_file_count`, `proposal_count`, `issue_count` trên `scan_run`.
3. Commit; chỉ sau đó worker mới nhận chunk tiếp.

Unique key `(scan_run_id, source_relative_path)` giữ retry không tạo proposal trùng. Checkpoint là mốc dữ liệu đã commit, không phải filesystem snapshot: implementation dev đầu tiên có thể rewalk từ root và dedupe record đã có sau restart. Resume chính xác theo directory partition chỉ là tối ưu tiếp theo, không chặn touchpoint này.

## 4. Worker crash và lease takeover

```mermaid
flowchart TD
    RUN["<font color='white'>Worker-1 xử lý chunk<br/>checkpoint đã commit</font>"]
    CRASH["<font color='white'>Crash hoặc mất mạng</font>"]
    EXPIRE["<font color='white'>lease_until hết hạn</font>"]
    CLAIM["<font color='white'>Worker-2 CAS claim<br/>worker_id mới</font>"]
    REWALK["<font color='white'>Rewalk root<br/>dedupe proposal cũ</font>"]
    CONTINUE["<font color='white'>Tiếp tục commit<br/>chunk mới</font>"]

    RUN -->|"Worker dừng"| CRASH
    CRASH -->|"Sau 30 giây"| EXPIRE
    EXPIRE -->|"Lease hợp lệ mới"| CLAIM
    CLAIM -->|"Khởi động lại discovery"| REWALK
    REWALK -->|"Bỏ duplicate DB"| CONTINUE

    style RUN fill:#FF9800,stroke:#fff,stroke-width:2px
    style CRASH fill:#E91E63,stroke:#fff,stroke-width:2px
    style EXPIRE fill:#E91E63,stroke:#fff,stroke-width:2px
    style CLAIM fill:#FF9800,stroke:#fff,stroke-width:2px
    style REWALK fill:#009688,stroke:#fff,stroke-width:2px
    style CONTINUE fill:#4CAF50,stroke:#fff,stroke-width:2px
```

Điều cần code là `UPDATE scan_run ... WHERE id = :scanId AND lease_until < now()` để claim, và mọi update chunk mang `worker_id` hiện tại. Không cần cố làm checkpoint path thành cursor hoàn hảo ngay từ đầu; rewalk + unique constraint đủ đơn giản để thông luồng, sau đó mới partition nếu full rewalk trở thành điểm nghẽn thật.

## 5. Review keyset

Baseline `Pageable`/OFFSET được thay bằng cursor. Composite index target là `(scan_run_id, source_relative_path, id)`.

```text
GET /api/v2/scans/{scanId}/proposals?limit=50

SELECT ...
FROM scan_proposal
WHERE scan_run_id = :scanId
ORDER BY source_relative_path, id
LIMIT 50;

GET /api/v2/scans/{scanId}/proposals?limit=50&cursor=<path,id>

SELECT ...
FROM scan_proposal
WHERE scan_run_id = :scanId
  AND (source_relative_path, id) > (:path, :id)
ORDER BY source_relative_path, id
LIMIT 50;
```

Cursor trả về `nextCursor` từ row cuối. UI chỉ cần Next/Previous theo filter/sort cố định; không cần hỗ trợ nhảy đến trang số bất kỳ trong SC-01.

## 6. Bulk decision và transactional outbox

`POST /api/v2/scans/{scanId}/decisions` hiện tại không phù hợp vì materialize toàn bộ proposal. Target API là `POST /api/v2/scans/{scanId}/bulk-decisions`; nó tạo `bulk_decision_job` rồi trả `202 {bulkJobId}`.

```mermaid
flowchart TD
    REQ["<font color='white'>Admin gửi filter<br/>approve hoặc reject</font>"]
    JOB["<font color='white'>Tạo bulk_decision_job<br/>PENDING</font>"]
    CLAIM["<font color='white'>Keyset claim<br/>500 proposal</font>"]
    TX["<font color='white'>Local transaction<br/>decision + outbox + progress</font>"]
    DB[("<font color='white'>scan_db</font>")]
    LOOP["<font color='white'>Chunk tiếp theo<br/>đến khi DONE</font>"]

    REQ -->|"POST bulk-decisions"| JOB
    JOB -->|"Worker bắt đầu"| CLAIM
    CLAIM -->|"Chunk đã chốt"| TX
    TX -->|"Commit"| DB
    DB -->|"Còn proposal"| LOOP
    LOOP -->|"Claim chunk mới"| CLAIM

    style REQ fill:#2196F3,stroke:#fff,stroke-width:2px
    style JOB fill:#FF9800,stroke:#fff,stroke-width:2px
    style CLAIM fill:#FF9800,stroke:#fff,stroke-width:2px
    style TX fill:#9C27B0,stroke:#fff,stroke-width:2px
    style DB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style LOOP fill:#4CAF50,stroke:#fff,stroke-width:2px
```

Mỗi chunk update/insert các bảng target: `scan_decision`, `scan_outbox_event`, `bulk_decision_job`. Approve tạo event; reject chỉ tạo decision. Phạm vi bulk phải được chốt khi tạo job để UI đổi filter sau đó không làm job đang chạy đổi tập dữ liệu.

## 7. Outbox đến Catalog

Outbox không nằm trong transaction Kafka. Worker lấy event có `published_at IS NULL`, gửi topic `media.file.discovered.v2`, broker ACK xong mới set `published_at`. Nếu process chết giữa hai bước, event có thể publish lại; Catalog xử lý bằng `eventId` nên không tạo asset/subject business trùng.

```mermaid
flowchart TD
    OUTBOX[("<font color='white'>scan_outbox_event<br/>published_at IS NULL</font>")]
    PUB["<font color='white'>Publisher lấy batch<br/>SKIP LOCKED khi đa worker</font>"]
    KAFKA[("<font color='white'>Kafka topic<br/>media.file.discovered.v2</font>")]
    ACK["<font color='white'>Broker ACK</font>"]
    MARK["<font color='white'>Set published_at</font>"]
    CATALOG["<font color='white'>Catalog dedupe eventId<br/>upsert canonical data</font>"]

    OUTBOX -->|"Pending event"| PUB
    PUB -->|"Publish"| KAFKA
    KAFKA -->|"Acknowledgement"| ACK
    ACK -->|"Mark published"| MARK
    KAFKA -->|"Consume"| CATALOG

    style OUTBOX fill:#9C27B0,stroke:#fff,stroke-width:2px
    style PUB fill:#E91E63,stroke:#fff,stroke-width:2px
    style KAFKA fill:#E91E63,stroke:#fff,stroke-width:2px
    style ACK fill:#4CAF50,stroke:#fff,stroke-width:2px
    style MARK fill:#9C27B0,stroke:#fff,stroke-width:2px
    style CATALOG fill:#2196F3,stroke:#fff,stroke-width:2px
```

## 8. Thứ tự lập plan

1. Touchpoint 1–2: mở rộng `scan_run`, API start/status, lease manager và lifecycle worker.
2. Touchpoint 3–5: tách walker, inventory matcher, parser/chunk writer, queue bounded và checkpoint.
3. Touchpoint 6: đổi proposal API sang keyset cursor + composite index.
4. Touchpoint 7: schema/API/worker cho `bulk_decision_job`.
5. Touchpoint 8: upgrade publisher cho bulk backlog và xác nhận event `media.file.discovered.v2` với Catalog.

## Tham chiếu

- [Overview SC-01](./01-deep-dive.md)
- `apps/scan-service/CONTEXT.md`
- `apps/scan-service/.../ScanExecutor.java`, `ScanService.java`, `ScanDecisionService.java`, `ScanOutboxPublisher.java`
- `docs/contracts/openapi/scan-v1.yaml`
