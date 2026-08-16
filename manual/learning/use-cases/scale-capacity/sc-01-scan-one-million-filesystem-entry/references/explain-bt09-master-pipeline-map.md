# Giải thích chi tiết: Master Pipeline Map toàn cảnh 7 lát cắt của BT-09

> **Mục đích tài liệu**: Dành cho người dùng / kiến trúc sư đọc hiểu toàn cảnh luồng đi của 1.000.000 bản ghi qua từng service, vị trí của 7 lát cắt (`BT-09A` → `BT-09G`) và 5 mốc Watermark.  
> **Router ngắn gọn cho Agent**: [`08-approve-1m-context.md`](../08-approve-1m-context.md).

---

## 1. Master Pipeline Map toàn cảnh

```mermaid
flowchart TD
    UI(["User: POST /approve<br/>(1.000.000 records)"])
    
    subgraph STAGE_A["BT-09A: Contract &amp; Watermarks"]
        W_ACC(["[1] ACCEPTED<br/>(HTTP 202, Bắt đầu SLO)"])
    end

    subgraph STAGE_B["BT-09B: Scan Chunking"]
        S_CHUNK["Scan Chunk Executor<br/>(25.000 items/chunk)"]
        S_DB[("scan_db<br/>(decision + outbox)")]
        W_SCAN(["[2] APPROVAL_COMMITTED<br/>(Scan xong 1M file)"])
    end

    subgraph STAGE_C["BT-09C: Continuous Drain"]
        RELAY["Outbox Drain Relay<br/>(Async in-flight 2.000)"]
        K1{{"Kafka Topic:<br/>media.file.discovered.v2<br/>(1M messages)"}}
    end

    subgraph STAGE_D["BT-09D: Catalog Coalescing"]
        C_CONS["Catalog Batch Consumer<br/>(In-Memory Coalescing)"]
        C_DB[("catalog_db<br/>(Bulk canonical writes)")]
        W_CAT(["[3] CATALOG_COMMITTED<br/>(chốt expectedSubjectCount)"])
        K2{{"Kafka Topic:<br/>media.subject.changed.v2<br/>(~148K snapshots)"}}
    end

    subgraph STAGE_E["BT-09E: Query Bulk &amp; O(1) Cache"]
        Q_CONS["Query Bulk Consumer<br/>(COPY / Upsert Version Guard)"]
        Q_DB[("query_db<br/>(Read Model ready)")]
        Q_CACHE(("Redis Cache<br/>(O(1) Generation Switch)"))
        BARRIER{"Equality Gate:<br/>projected == expected<br/>VÀ DLT == 0 ?"}
        W_READY(["[4] QUERY_DB_READY<br/>(Dừng đồng hồ đo SLO)"])
        Q_ES>"[5] SEARCH_READY<br/>(Async Elasticsearch Bulk)"]
    end

    subgraph STAGE_F["BT-09F: Error Isolation &amp; DLT"]
        DLT{{"*.DLT Topic<br/>(Poison pill isolation)"}}
        BLOCKED(["Watermark: BLOCKED<br/>(Khi có DLT &gt; 0)"])
        REPLAY["Idempotent Replay Runbook"]
    end

    subgraph STAGE_G["BT-09G: Scale Ladder"]
        LADDER["Scale Ladder Benchmark:<br/>1K -> 5K -> 50K -> 250K -> 1M<br/>(Đo p95/p99, WAL, Pool)"]
    end

    UI --> W_ACC --> S_CHUNK
    S_CHUNK --> S_DB --> W_SCAN
    W_SCAN --> RELAY --> K1
    K1 --> C_CONS
    C_CONS --> C_DB --> W_CAT --> K2
    K2 --> Q_CONS
    Q_CONS --> Q_DB --> Q_CACHE --> BARRIER
    BARRIER -->|"Đủ 100%"| W_READY
    W_READY -.-> Q_ES

    C_CONS -.->|"Poison error"| DLT
    Q_CONS -.->|"Poison error"| DLT
    DLT --> BLOCKED
    REPLAY -.-> K1
    REPLAY -.-> K2

    W_READY -.-> LADDER

    style UI fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style W_ACC fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style S_CHUNK fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style S_DB fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style W_SCAN fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style RELAY fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style K1 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style C_CONS fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style C_DB fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style W_CAT fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style K2 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style Q_CONS fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style Q_DB fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style Q_CACHE fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style BARRIER fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style W_READY fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style Q_ES fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
    style DLT fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style BLOCKED fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style REPLAY fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style LADDER fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
```

---

## 2. Ý nghĩa kỹ thuật của từng lát cắt

| Lát cắt | Service sở hữu | Trách nhiệm kỹ thuật cốt lõi |
| :--- | :--- | :--- |
| **`BT-09A`** | Cross-service | Thiết kế khung Hợp đồng & Watermarks (cấp vé `[1] ACCEPTED`, chốt 5 mốc). |
| **`BT-09B`** | `scan-service` | Ghi decision + outbox atomic theo bounded chunks 25.000 items $\to$ `[2] APPROVAL_COMMITTED`. |
| **`BT-09C`** | `scan-service` Relay | Continuous Drain Relay xả liên tục lên Kafka với in-flight buffer 2.000 messages. |
| **`BT-09D`** | `catalog-service` | In-Memory Batch Coalescing gom 1M file thành ~148K subject $\to$ `[3] CATALOG_COMMITTED`. |
| **`BT-09E`** | `query-service` | Bulk Projection (COPY/Upsert), Equality Gate $\to$ `[4] QUERY_DB_READY` & `[5] SEARCH_READY`. |
| **`BT-09F`** | Toàn hệ thống | Cô lập Poison pill sang DLT, chuyển `BLOCKED` để bảo vệ data, Runbook replay. |
| **`BT-09G`** | Hạ tầng & Benchmark | Chạy tải thực nghiệm 1K $\to$ 1M để chứng minh đạt mục tiêu SLO 30 giây. |
