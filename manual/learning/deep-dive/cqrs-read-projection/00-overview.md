# 📖 CQRS Lite & Eventual Consistency Overview

Tài liệu giải thích mô hình **CQRS Lite** trong Backend V2, lý do tách biệt Write Side (`catalog-service`) và Read Side (`query-service`), khoảng trễ **Eventual Consistency** và cách xử lý trong thực tế.

---

## 1. CQRS Lite là gì? Tại sao không query trực tiếp Write DB?

Trong kiến trúc Monolith truyền thống, ứng dụng thường dùng chung 1 Database và 1 Data Model cho cả việc Ghi (Create/Update) và Đọc (Search/Filter/List).Khi dữ liệu phình to và lượng truy vấn đọc tăng gấp 50-100 lần lượng ghi, việc JOIN nhiều bảng trên Write DB sẽ gây ra:
- **Lock contention**: Các lệnh UPDATE/INSERT làm khóa hàng/khóa bảng, khiến các lệnh SELECT bị treo.
- **Query Complexity**: Phải JOIN liên tục giữa `media_subject` và `media_asset` kèm phân trang, sắp xếp phức tạp.

### Mô hình CQRS Lite trong Backend V2

```mermaid
flowchart TB
    CLIENT["<font color='white'>Frontend Web / Gallery App</font>"]

    subgraph WRITE_SIDE["Write Side (Catalog Service)"]
        CATALOG_API["<font color='white'>Catalog REST API<br/>(Create / Update Subject)</font>"]
        CATALOG_DB[("<font color='white'>catalog_db<br/>(PostgreSQL Canonical Model)</font>")]
        OUTBOX["<font color='white'>catalog_outbox_event</font>"]
        CATALOG_API -->|Local Tx| CATALOG_DB
        CATALOG_API -->|Local Tx| OUTBOX
    end

    subgraph KAFKA_BUS["Event Bus"]
        KAFKA_TOPIC["<font color='white'>Kafka Topic<br/>media.subject.changed.v1</font>"]
    end

    subgraph READ_SIDE["Read Side (Query Service)"]
        CONSUMER["<font color='white'>Projection Consumer</font>"]
        QUERY_DB[("<font color='white'>query_db<br/>(PostgreSQL Read Model)</font>")]
        ES[("<font color='white'>Elasticsearch<br/>(Fast Search Index)</font>")]
        QUERY_API["<font color='white'>Query REST API<br/>(Search & Detail)</font>"]
        
        CONSUMER -->|Update Projection| QUERY_DB
        QUERY_DB -->|Bulk Sync| ES
        QUERY_API -->|Fast Hit Search| ES
        QUERY_API -->|Hydrate Detail| QUERY_DB
    end

    CLIENT -->|Write Request| CATALOG_API
    OUTBOX -->|Relay Event| KAFKA_TOPIC
    KAFKA_TOPIC --> CONSUMER
    CLIENT -->|Read Request| QUERY_API

    style CLIENT fill:#4CAF50,stroke:#fff,stroke-width:2px
    style CATALOG_API fill:#FF9800,stroke:#fff,stroke-width:2px
    style CATALOG_DB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style OUTBOX fill:#E91E63,stroke:#fff,stroke-width:2px
    style KAFKA_TOPIC fill:#E91E63,stroke:#fff,stroke-width:2px
    style CONSUMER fill:#2196F3,stroke:#fff,stroke-width:2px
    style QUERY_DB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ES fill:#009688,stroke:#fff,stroke-width:2px
    style QUERY_API fill:#2196F3,stroke:#fff,stroke-width:2px
```

---

## 2. Bản chất của Eventual Consistency (Nhất quán sau cùng)

### 2.1. Đánh đổi giữa Strong Consistency và Availability/Performance
- **Strong Consistency (Nhất quán mạnh)**: Bắt buộc client chờ ghi xong ở cả DB Đọc và DB Ghi mới trả về thành công. Làm tăng Latency và giảm sức chịu tải.
- **Eventual Consistency (Nhất quán sau cùng)**: Khi Ghi thành công ở `catalog-service`, API trả lời 바로 `200 OK`. Event truyền qua Kafka và cập nhật Read Projection sau vài milisecond.

### 2.2. Xử lý UI UX khi gặp khoảng trễ (Replication Lag)
Trên giao diện Frontend:
1. **Optimistic UI Update**: Giao diện tự thêm item mới vào danh sách tạm thời trước khi Query API trả về.
2. **Polling / WebSocket Notification**: Lắng nghe SSE/WebSocket notification khi Event snapshot đã được Query Service xử lý xong.

---

## 3. Bản so sánh Canonical Write Model vs Read Projection

| Tiêu Chí | Canonical Write Model (`catalog_db`) | Read Projection (`query_db`) |
| :--- | :--- | :--- |
| **Mục đích** | Đảm bảo tính toàn vẹn dữ liệu gốc (SSOT) | Phục vụ truy vấn đọc & tìm kiếm siêu tốc |
| **Cấu trúc Dữ liệu** | Chuẩn hóa Normalized (3NF): `media_subject`, `media_asset` | Làm phẳng Denormalized: 1 bản ghi chứa sẵn JSON assets |
| **Chế độ Quyền** | Write-Heavy (Chỉ Catalog Service được sửa) | Read-Heavy (Query Service chỉ đọc & cập nhật từ Event) |
| **Chỉ mục Index** | Chỉ mục B-Tree trên Primary Key & Identity Key | Chỉ mục GIN, Text Search & Elasticsearch Inverted Index |
