# LinkedIn xử lý pipeline dữ liệu lớn như thế nào?

Ngày: 2026-08-14

## Tóm tắt

LinkedIn tách source-of-truth, change capture, stream processing và derived serving stores.

- Espresso là source-of-truth phân tán.
- Databus/Kafka vận chuyển thay đổi theo partition, checkpoint và replay.
- Samza xử lý stream theo partition, giữ state cục bộ và changelog.
- Venice, cache và search là derived state, thường eventual consistency.

LinkedIn từng công bố Kafka đạt hơn 1 nghìn tỷ message/ngày và peak khoảng 4,5 triệu message/giây. Đây là throughput Kafka, không phải SLO end-to-end cho projection DB/search.

## 1. Kiến trúc tổng thể

```mermaid
flowchart TB
    WRITE(["Ứng dụng ghi"])
    ESPRESSO[("Espresso<br/>primary store")]
    CDC{{"Databus<br/>change capture"}}
    KAFKA{{"Kafka<br/>durable log"}}
    SAMZA["Samza<br/>stream processor"]
    VENICE[("Venice<br/>derived store")]
    SEARCH>"Galene<br/>search"]
    READ(["Ứng dụng đọc"])
    WRITE --> ESPRESSO --> CDC --> KAFKA --> SAMZA --> VENICE --> READ
    SAMZA --> SEARCH --> READ
    style WRITE fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style ESPRESSO fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style CDC fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style KAFKA fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style SAMZA fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style VENICE fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style SEARCH fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
    style READ fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
```

Kafka/Databus là log durable, không thay thế database nguồn. Downstream có thể replay để xây lại state.

## 2. Họ vẫn dùng DB, Redis và search như thế nào?

- Primary store được partition theo key.
- Secondary index được cập nhật theo commit của base data khi cần invariant mạnh.
- Cache/search/derived view thường được cập nhật bất đồng bộ.
- Stream processor dùng cùng partition key để giảm remote lookup và lock contention.

| Lớp | Cách xử lý | Bài học cho dự án |
| --- | --- | --- |
| Primary DB | Transaction trên partition | Approve + outbox trong transaction cục bộ |
| Transport | Partition, batching, checkpoint | Relay/consumer bounded batch |
| Stream processor | Coalesce theo key | Catalog/Query gom theo subject |
| Derived store | Batch push hoặc streaming push | Query DB/Search có watermark riêng |
| Rebuild | Snapshot/bootstrap rồi replay | Staging/chunk/replay cho SC-01 |

Kafka producer batching không có nghĩa consumer đã bulk-write vào DB.

## 3. Bootstrap và replay

LinkedIn dùng snapshot/backup để dựng derived dataset mới, trong khi online stream vẫn chạy. Khi bắt kịp, replay update mới hơn rồi switch version.

```mermaid
flowchart TB
    SNAP[("Primary snapshot")]
    BOOT["Bootstrap processor<br/>bounded chunks"]
    LIVE{{"Live Kafka / Databus"}}
    REPLAY["Replay + version guard"]
    NEW[("Candidate dataset")]
    CHECK{"Caught up?"}
    SERVE(["Atomic serving switch"])
    SNAP --> BOOT --> NEW --> CHECK
    LIVE --> REPLAY --> NEW
    CHECK -->|"Chưa"| REPLAY
    CHECK -->|"Rồi"| SERVE
```

## 4. Venice: batch và nearline

Venice nhận full push và streaming push. Version mới được dựng background, nearline update được replay trước khi switch serving.

```mermaid
flowchart TB
    BATCH(["Full push"])
    STREAM{{"Kafka nearline stream"}}
    BUILD["Build version N+1"]
    APPLY["Apply stream updates"]
    READY{"Replay caught up?"}
    ACTIVE[("Active version")]
    SWITCH(["Atomic version switch"])
    BATCH --> BUILD --> APPLY --> READY
    STREAM --> APPLY
    READY -->|"Chưa"| APPLY
    READY -->|"Rồi"| SWITCH --> ACTIVE
```

## 5. Đối chiếu với dự án

### Hiện trạng

```mermaid
flowchart TB
    A(["Approve 5.000"])
    S["Scan transaction"]
    O{{"Outbox batch 500"}}
    K{{"Kafka"}}
    C["Catalog per-record tx"]
    Q["Query per-record tx"]
    R(("Redis DEL per subject"))
    E>"Search sequential write"]
    A --> S --> O --> K --> C --> Q --> R
    Q --> E
```

### Kiến trúc nên hướng tới

```mermaid
flowchart TB
    A(["Approve + batchId"])
    S["Bulk decision + outbox"]
    K{{"Continuous Kafka drain"}}
    C["Catalog batch + coalesce subject"]
    CD[("Catalog set-based upsert")]
    ST[/"Query staging COPY"/]
    Q[("Query bulk upsert + version guard")]
    R(("Redis pipeline after commit"))
    W(["QUERY_DB_READY"])
    E>"Search Bulk API / SEARCH_READY"]
    A --> S --> K --> C --> CD --> ST --> Q --> R --> W
    Q --> E
```

## 6. Bài học áp dụng

- Giữ PostgreSQL là source-of-truth của từng service; giữ transactional outbox và idempotency.
- Batch listener, dedupe set-based, coalesce theo subject và bulk upsert.
- Có batchId/watermark QUERY_DB_READY; tách SEARCH_READY.
- Rebuild bằng staging/chunk/replay, không dùng transaction khổng lồ.
- Chỉ tăng partition/concurrency sau benchmark p95/p99, lag, outbox age, DB pool wait và WAL/IOPS.

## Tài liệu công khai

- [Introducing Espresso](https://engineering.linkedin.com/espresso/introducing-espresso-linkedins-hot-new-distributed-document-store)
- [Kafka at LinkedIn](https://engineering.linkedin.com/apache-kafka/how-we_re-improving-and-advancing-kafka-linkedin)
- [Stream Processing Hard Problems Part 1](https://www.linkedin.com/blog/engineering/data-streaming-processing/stream-processing-hard-problems-part-1)
- [Stream Processing Hard Problems Part 2](https://www.linkedin.com/blog/engineering/archive/stream-processing-hard-problems-part-ii-data-access)
- [Open Sourcing Venice](https://www.linkedin.com/blog/engineering/open-source/open-sourcing-venice-linkedin-s-derived-data-platform)
- [Venice Hybrid](https://www.linkedin.com/blog/engineering/open-source/venice-hybrid-doing-lambda-better)
- [LinkedIn Databus](https://github.com/linkedin/databus)
