# 🧭 Deep-Dive: Toàn cảnh Kiến trúc & Data Pipeline Scan Preview 1.000.000 Files (< 30s)

> **Mục tiêu tài liệu**: Bóc tách toàn diện từ First Principles kiến trúc, luồng dữ liệu (Data Pipeline), trục xương sống (Backbone), mô hình xử lý Đồng bộ/Bất đồng bộ (Sync/Async) và các nhánh rẽ phụ (Side-Branches) của tiến trình Scan Preview trong `scan-service`.  
> **Áp dụng dự án**: `file_mngt_microservice` (PostgreSQL 17 / Java 25 Virtual Threads / Workload SC-01).

---

## 1. Bản đồ Cấp 1 (Vĩ mô): Trục xương sống & 4 Nhánh rẽ phụ

Toàn bộ pipeline Scan Preview được điều phối dựa trên **1 Trục xương sống chính (Backbone 6 Phase)** và **4 Nhánh rẽ phụ (Ancillary Lanes)**:

```mermaid
flowchart TD
    START(["Khởi tạo Scan 1M<br/>POST /previews"])
    
    subgraph BACKBONE["Trục xương sống chính (Backbone Pipeline)"]
        direction TB
        S1["[Phase 1] Cấp Lease<br/>Khóa độc quyền<br/>rootKey (60s)"]
        S2["[Phase 2] Discovery<br/>Stream COPY 1M<br/>(2 Segments 500k)"]
        S3["[Phase 3] Set Diff<br/>SQL Anti-Join lọc<br/>changed files"]
        S4["[Phase 4] Parallel<br/>8 Virtual Threads<br/>(100k items/chunk)"]
        S5["[Phase 5] Direct COPY<br/>Ghi proposal + issue<br/>(Binary Stream)"]
        S6["[Phase 6] Complete<br/>Cập nhật COMPLETED<br/>&amp; Dọn Staging"]
        
        S1 --> S2 --> S3 --> S4 --> S5 --> S6
    end

    subgraph SIDE_BRANCHES["4 Nhánh rẽ phụ (Ancillary Lanes)"]
        direction TB
        B1["[Nhánh 1] SSE Stream<br/>Bắn tiến độ realtime<br/>cho giao diện UI"]
        B2["[Nhánh 2] Catalog<br/>Micro-batch 500<br/>kiểm tra trùng lặp"]
        B3["[Nhánh 3] Heartbeat<br/>Gia hạn lease 60s<br/>chống zombie worker"]
        B4["[Nhánh 4] Review Queue<br/>Dựng ngầm Gen 2<br/>&amp; Swap tức thì"]
    end

    START --> S1
    S2 -.->|"Bắn tiến độ"| B1
    S4 -.->|"Kiểm tra trùng"| B2
    S2 & S5 -.->|"Gia hạn lease"| B3
    S6 ==>|"Enqueue Task"| B4

    style START fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style S1 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style S2 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style S3 fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style S4 fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style S5 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style S6 fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style B1 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style B2 fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style B3 fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
    style B4 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
```

### 📋 Bảng tra cứu các Chặng trên Trục xương sống:

| Chặng | Tên giai đoạn | Trách nhiệm kỹ thuật cốt lõi | Thời gian thực thi | Luồng con chi tiết |
| :--- | :--- | :--- | :---: | :---: |
| **Phase 1** | Cấp Lease Fencing | Khóa phân vùng `rootKey`, chặn 2 scan chạy đè lên nhau | $\sim 5\text{ms}$ | Mục 3 |
| **Phase 2** | Discovery & Stream COPY | `Files.walkFileTree` $\to$ Bounded Queue $\to$ Direct COPY 2 segments (500k/segment) vào bảng UNLOGGED | $\sim 1,8\text{s} - 6,0\text{s}$ | **Sub-flow A (Mục 4)** |
| **Phase 3** | Set-based Diff | 1 câu SQL `INSERT ... SELECT` lọc các file mới/đổi vào `scan_inventory_diff_stage` | $\sim 0,4\text{s} - 6,5\text{s}$ | **Sub-flow B (Mục 5)** |
| **Phase 4** | Parallel Analyzer | Đọc từng page Keyset 100.000 items, chia 8 partition trên Java Virtual Threads (~12.5k items/thread) | $\sim 8,5\text{s} - 18,5\text{s}$ | **Sub-flow B (Mục 5)** |
| **Phase 5** | Direct COPY Persistence | Ghi trực tiếp `scan_proposal` và `scan_issue` bằng PostgreSQL `COPY`, commit `@Transactional(REQUIRES_NEW)` | $\sim 4,2\text{s} - 8,8\text{s}$ | **Sub-flow C (Mục 6)** |
| **Phase 6** | Complete & Hand-off | Đánh dấu `COMPLETED`, dọn staging và ném Task dựng Review Projection vào hàng đợi | $\sim 1,5\text{s}$ | **Sub-flow D (Mục 7)** |


> [!TIP]
> ### 💡 Từ điển Thuật ngữ & Mental Model (Gốc từ & Cách liên tưởng)
>
> 1. **Lease Fencing (Hàng rào kiểm tra hợp đồng thuê)**:
>    - **Nghĩa tiếng Anh thuần**: `Lease` là *hợp đồng thuê nhà/thuê đất có thời hạn*; `Fencing` là *dựng hàng rào ngăn cách / rào chắn bảo vệ*.
>    - **Trong ngữ cảnh dự án**: Một Worker muốn quét thư mục `ROOT_VIDEO` phải "thuê" quyền độc quyền trong 60 giây (`lease_until = NOW() + 60s`). "Fencing" là rào chắn: trước khi ghi dữ liệu, Worker phải trình vé thuê (`WHERE lease_owner = :workerId AND lease_until > NOW()`). Nếu Worker bị đơ/lag quá 60s, hợp đồng hết hạn $\to$ Hàng rào sập xuống chặn đứng, không cho ghi bậy vào DB.
>    - **Tại sao gọi như vậy**: Giống như bạn thuê phòng khách sạn có khóa thẻ từ theo giờ. Hết giờ thuê mà chưa gia hạn, thẻ bị vô hiệu hóa (Fencing), bảo vệ không cho bạn vào phòng nữa để giao cho khách mới.
>    - **Cách liên tưởng**: *"Trình vé thuê còn hạn ở cổng hàng rào"*.
>
> 2. **Backbone Pipeline (Đường ống xương sống)**:
>    - **Nghĩa tiếng Anh thuần**: `Backbone` là *xương sống của cơ thể*; `Pipeline` là *đường ống dẫn nước / dây chuyền sản xuất*.
>    - **Trong ngữ cảnh dự án**: Là trục thực thi chính yếu, bắt buộc phải chạy tuần tự qua 6 chặng để hoàn thành 1 đợt scan. Nếu gãy 1 đốt xương sống thì cả tiến trình scan dừng lại.
>
> 3. **Ancillary Lanes (Các làn xe phụ trợ)**:
>    - **Nghĩa tiếng Anh thuần**: `Ancillary` là *phụ trợ / thứ yếu*; `Lane` là *làn đường xe chạy*.
>    - **Trong ngữ cảnh dự án**: Là các tác vụ chạy rẽ nhánh bên cạnh trục chính (như bắn SSE cho UI, gia hạn lease, dựng projection). Xe trên làn phụ dù có trục trặc (ví dụ mất mạng SSE) thì xe trên làn chính (Backbone) vẫn phóng thẳng về đích!

---

## 2. Bản đồ Cấp 2 (Vi phẫu): Chi tiết Mô hình Sync / Async / Parallel

Đi sâu vào bên trong Worker, dưới đây là **bóc tách vi phẫu từng luồng xử lý (Thread / I/O / Queue / DB)**:

```mermaid
flowchart TD
    subgraph PHASE_1_HTTP["Chặng 1: HTTP API (Sync &lt; 10ms)"]
        direction TB
        REQ["[1] Nhận Request<br/>POST /previews"]
        SNAP["[2] Fetch Registry<br/>(Sync Catalog)"]
        LEASE["[3] Cấp Lease DB<br/>(Sync Fencing)"]
        RESP["[4] Trả HTTP 202<br/>(Bàn giao Worker)"]
        
        REQ --> SNAP --> LEASE --> RESP
    end

    subgraph PHASE_2_DISCOVERY["Chặng 2: Discovery 1M (Producer-Consumer)"]
        direction TB
        T1["Thread 1: Walker<br/>Đọc đĩa liên tục<br/>(Producer I/O)"]
        Q(("Queue đệm<br/>(Cap 1.000)"))
        T2["Thread 2: COPY<br/>Ghi UNLOGGED<br/>scan_inventory_stage"]
        
        T1 -->|"queue.put()"| Q
        Q -->|"queue.take()"| T2
    end

    subgraph PHASE_3_DIFF["Chặng 3: Set-based Diff (Sync DB Call)"]
        SQL["SQL Hash Anti-Join<br/>Trừ tập hợp trên DB<br/>(Sync đợi DB)"]
    end

    subgraph PHASE_4_ANALYZE["Chặng 4: Parallel Analyzer (Virtual Threads)"]
        direction TB
        SPLIT["Chia 8 Partitions<br/>(100k items/chunk<br/>~12.5k/thread)"]
        V1["Virtual Thread 1<br/>(Parse Regex CPU)"]
        V2["Virtual Thread 2<br/>(Parse Regex CPU)"]
        V8["Virtual Thread 8<br/>(Parse Regex CPU)"]
        CAT["Catalog Check<br/>(Sync HTTP 500<br/>trên mỗi luồng)"]
        JOIN["Join Barrier<br/>(Đợi 8 luồng xong)"]

        SPLIT --> V1 & V2 & V8
        V1 & V2 & V8 <--> CAT
        V1 & V2 & V8 --> JOIN
    end

    subgraph PHASE_5_PERSIST["Chặng 5: Commit DB (Sync Local Transaction)"]
        direction TB
        TX["Transaction Cục bộ<br/>@Transactional<br/>(REQUIRES_NEW)"]
        COPY_P["Direct COPY<br/>vào scan_proposal<br/>(Nhị phân)"]
        COPY_I["Direct COPY<br/>vào scan_issue<br/>(Nhị phân)"]
        INV_UP["Cập nhật<br/>scan_file_inventory<br/>&amp; Checkpoint"]

        TX --> COPY_P --> COPY_I --> INV_UP
    end

    subgraph PHASE_6_BRANCHES["Chặng 6: Các luồng rẽ nhánh nền (Async Lanes)"]
        direction TB
        SSE["SSE Progress Hub<br/>Bắn % cho UI<br/>(Async non-blocking)"]
        PROJ["Projection Worker<br/>Dựng ngầm &amp; Swap<br/>(@Scheduled độc lập)"]
    end

    RESP ==>|"Kích hoạt luồng nền"| T1
    T2 ==>|"Discovery xong 1M"| SQL
    SQL ==>|"Có changed set"| SPLIT
    JOIN ==>|"Gom xong kết quả"| TX
    TX -.->|"Bắn tiến độ"| SSE
    TX ==>|"Hoàn tất Run"| PROJ

    style REQ fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style SNAP fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style LEASE fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style RESP fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style T1 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style Q fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style T2 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style SQL fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style SPLIT fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style V1 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style V2 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style V8 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style CAT fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style JOIN fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style TX fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style COPY_P fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style COPY_I fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style INV_UP fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style SSE fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style PROJ fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
```

### 📊 Bảng phân tích chi tiết cơ chế Sync / Async / Concurrent:

| Chặng xử lý | Chi tiết hoạt động | Cơ chế thực thi | Thời gian |
| :--- | :--- | :---: | :---: |
| **1. HTTP Controller** | Kiểm tra Snapshot Catalog + Cấp Lease Fencing + Trả HTTP `202`. | **SYNC** (Chặn request client $< 10\text{ms}$) | $< 10\text{ms}$ |
| **2. Discovery Phase** | **Thread 1 (Walker)** quét đĩa đẩy vào Queue; **Thread 2 (COPY)** rút Queue ghi PostgreSQL `COPY`. | **ASYNC** giữa Đĩa và Database (Tách rời I/O qua Queue) | $\sim 1,8\text{s} - 6,0\text{s}$ (1M files) |
| **3. Set-based Diff** | Gửi 1 câu SQL `INSERT ... SELECT` sang PostgreSQL so sánh fingerprint. | **SYNC** (Luồng Worker đợi DB trả kết quả) | $\sim 0,4\text{s} - 6,5\text{s}$ |
| **4. Java Analyzer** | Đọc Keyset 100k items, chia 8 phân vùng chạy song song trên **8 Virtual Threads (Java 25)**. | **PARALLEL / CONCURRENT** (Đa nhân CPU) | $\sim 8,5\text{s} - 18,5\text{s}$ |
| **- Catalog Existence** | Từng Virtual Thread gọi HTTP sang Catalog theo micro-batch 500. | **SYNC** bên trong từng Virtual Thread | $\sim 15\text{ms}$/batch |
| **- Join Barrier** | Luồng Worker chính đứng đợi cả 8 Virtual Threads hoàn tất. | **SYNC BARRIER** (Structured Concurrency) | Điểm chốt chặn |
| **5. Commit DB Chunk** | Mở Transaction ghi `scan_proposal`, `scan_issue`, cập nhật `scan_run`. | **SYNC** (Local Transaction `@Transactional`) | $\sim 1,5\text{s}$/chunk (100k items) |
| **6. SSE Progress** | Phát event tiến độ phần trăm (`0% -> 100%`) cho trình duyệt. | **ASYNC** (Non-blocking qua `SseEmitter`) | Best-effort ($< 1\text{ms}$) |
| **7. Review Projection** | `@Scheduled` Worker bốc Task dựng ngầm `generation = 2` và `swapRoot()`. | **ASYNC HOÀN TOÀN** (Tách rời khỏi tiến trình Scan) | Chạy ngầm độc lập |

> [!TIP]
> ### 💡 Từ điển Thuật ngữ & Mental Model (Vi phẫu Concurrency)
>
> 1. **Producer - Consumer (Mô hình Nhà sản xuất - Người tiêu thụ qua Băng chuyền)**:
>    - **Nghĩa tiếng Anh thuần**: `Producer` là *người sản xuất/chế tạo ra hàng hóa*; `Consumer` là *người mua/ăn/tiêu thụ hàng hóa*.
>    - **Trong ngữ cảnh dự án**: Luồng 1 (Walker) đóng vai trò Producer chuyên đọc đường dẫn từ ổ cứng rồi thả vào khay đệm (`ArrayBlockingQueue`). Luồng 2 (COPY Writer) đóng vai trò Consumer chuyên bốc từ khay ra nạp vào PostgreSQL.
>    - **Tại sao gọi như vậy**: Tách rời 2 người làm 2 việc khác nhau. Tốc độ đọc ổ cứng nhanh không bị phụ thuộc vào tốc độ ghi Database; không bên nào phải đứng chờ bên nào.
>    - **Cách liên tưởng**: *"Quán lẩu băng chuyền: Đầu bếp cứ đặt đĩa thịt lên băng chuyền (Producer), khách cứ gắp đĩa xuống ăn (Consumer). Đầu bếp không cần đứng đợi khách nhai xong mới làm đĩa tiếp theo"*.
>
> 2. **Set-based Diff (Phép trừ tập hợp trên Database) vs. Row-by-row**:
>    - **Nghĩa tiếng Anh thuần**: `Set-based` là *dựa trên lý thuyết tập hợp (toán học A, B)*; `Diff` (Difference) là *sự khác biệt / phép trừ tập hợp*.
>    - **Trong ngữ cảnh dự án**: Thay vì kéo 1 triệu dòng lên Java rồi dùng vòng lặp `for` so sánh từng dòng (Row-by-row tốn $> 30\text{s}$), ta bắt Database thực hiện 1 phép toán đại số quan hệ: $\text{Tập file vừa quét} - \text{Tập file cũ} = \text{Tập file mới/đổi}$.
>    - **Tại sao gọi như vậy**: Triết lý thiết kế của cơ sở dữ liệu quan hệ (RDBMS) là xử lý theo khối tập hợp dữ liệu trong RAM của DB kernel thay vì xử lý tuần tự từng bản ghi.
>    - **Cách liên tưởng**: *"Dùng rây lọc hạt cát: Đổ cả xô cát qua rây 1 lần (Set-based) thay vì nhặt từng hạt cát lên soi (Row-by-row)"*.
>
> 3. **Structured Concurrency, Virtual Threads & Join Barrier**:
>    - **Nghĩa tiếng Anh thuần**: `Virtual Threads` là *luồng ảo siêu nhẹ (Java 25)*; `Structured Concurrency` là *xử lý đồng thời có tổ chức cấu trúc (mở ra cùng nhau, đóng lại cùng nhau)*; `Join Barrier` là *hàng rào hội quân / điểm danh*.
>    - **Trong ngữ cảnh dự án**: Khi cần phân tích 100.000 file trong 1 chunk, luồng chính chia làm 8 phần (~12.500 items/thread) và mở 8 Virtual Threads chạy song song. Luồng chính đứng đợi ở "Join Barrier" (chốt điểm danh); khi cả 8 luồng báo cáo hoàn tất thì mới cùng nhau bước tiếp sang bước Commit DB.
>    - **Tại sao gọi như vậy**: Tránh tình trạng "luồng mồ côi" (Orphan thread) chạy lạc trôi không ai quản lý khi gặp lỗi.
>    - **Cách liên tưởng**: *"Tổ đội đặc nhiệm chia 8 mũi tấn công và hẹn gặp nhau tại chốt tập kết (Join Barrier). Đúng giờ, đủ quân số 8 người mới cùng rút quân"*.


---

## 3. Chi tiết các Nhánh rẽ phụ (Ancillary Lanes)

### 🌿 Nhánh 1: SSE Realtime Progress Stream (`ScanRunSseHub`)
- **Vai trò**: Bắn tiến độ phần trăm (`0% -> 100%`) cho trình duyệt Admin qua giao thức Server-Sent Events (`GET /api/v2/scans/{scanId}/events`).
- **Nguyên tắc bất biến**: Là kênh **best-effort process-local**. Nếu trình duyệt mất mạng hoặc ngắt kết nối SSE, **tiến trình Scan vẫn chạy bình thường 100%**, không bị ảnh hưởng!

### 🌿 Nhánh 2: Catalog Existence Check (`CatalogExistenceClient`)
- **Vai trò**: Gửi micro-batch (tối đa 500 items/lần) sang `catalog-service` để kiểm tra file đã tồn tại trong Catalog chưa.
- **Tối ưu**: Chỉ kiểm tra những file bị thay đổi (changed set), không kiểm tra 1 triệu file.

### 🌿 Nhánh 3: Lease Heartbeat & Fencing (`ScanLeaseManager`)
- **Vai trò**: Mỗi khi hoàn tất 1 segment hoặc 1 chunk, worker tự động gia hạn `lease_until = NOW() + 60s`.
- **Bảo vệ**: Nếu worker bị treo quá 60s, database thu hồi lease để tránh zombie worker.

### 🌿 Nhánh 4: Review Projection & Generation Swap (`ScanReviewProjectionWorker`)
- **Vai trò**: Chạy ngầm sau khi Scan đã `COMPLETED` để chuẩn bị bảng hiển thị Review Queue cho Admin.

---

## 4. Luồng con A: Discovery & Staging Stream (Bí quyết nạp 1M file trong 1,8s)

```mermaid
flowchart TD
    DISK[("Ổ cứng Filesystem<br/>(1.000.000 files)")]
    
    DISK --> WALK["Files.walkFileTree<br/>(Đọc I/O liên tục)"]
    
    WALK --> QUEUE(("Queue đệm<br/>(Cap 1.000)"))
    
    QUEUE --> SEGMENT{"Chia 2 Segments<br/>(500k rows/seg)"}
    
    SEGMENT --> COPY1["PostgreSQL COPY #1<br/>(500k rows nhị phân)"]
    SEGMENT --> COPY2["PostgreSQL COPY #2<br/>(500k rows nhị phân)"]
    
    COPY1 & COPY2 --> STAGE[("scan_inventory_stage<br/>(Bảng UNLOGGED)")]

    style DISK fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style WALK fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style QUEUE fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style SEGMENT fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style COPY1 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style COPY2 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style STAGE fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
```

### 🔍 Cơ chế kỹ thuật:
1. **Không dùng JDBC `INSERT`**: Việc chạy 1.000.000 lệnh INSERT sẽ mất $> 40\text{ giây}$. Thay vào đó, hệ thống dùng giao thức nhị phân **PostgreSQL `COPY`** đổ trực tiếp vào bảng.
2. **Bảng `UNLOGGED`**: Bảng `scan_inventory_stage` được đánh dấu `UNLOGGED` (không ghi WAL log) $\implies$ Tốc độ ghi đạt **$\sim 300.000\text{ rows/giây}$**, toàn bộ 1 triệu file nạp vào DB chỉ mất đúng **$1,8\text{ giây}$**!

> [!TIP]
> ### 💡 Từ điển Thuật ngữ & Mental Model (Discovery & Staging)
>
> 1. **UNLOGGED Table (Bảng không ghi nhật ký phục hồi)**:
>    - **Nghĩa tiếng Anh thuần**: `Unlogged` là *không được ghi vào nhật ký (Log)*.
>    - **Trong ngữ cảnh dự án**: Bình thường PostgreSQL ghi mọi thay đổi vào file WAL (Write-Ahead Logging) trên đĩa để phòng khi mất điện thì khôi phục lại. Với bảng staging nháp, ta thêm từ khóa `UNLOGGED` để tắt tính năng này $\to$ Ghi thẳng vào RAM/Cache đĩa với tốc độ tối đa. Nếu server sập thì bảng nháp này tự bị xóa, không ảnh hưởng dữ liệu thật.
>    - **Tại sao gọi như vậy**: Vì nó bỏ qua bước ghi nhật ký (Log) để đổi lấy tốc độ ghi cực đại.
>    - **Cách liên tưởng**: *"Giấy nháp học sinh: Viết nháp nhanh tay rồi vứt đi, không cần đóng dấu lưu trữ vào sổ học bạ"*.
>
> 2. **Direct COPY / Binary Stream (Truyền tải dòng nhị phân trực tiếp)**:
>    - **Nghĩa tiếng Anh thuần**: `Direct` là *trực tiếp*; `COPY` là *lệnh sao chép khối lượng lớn*; `Binary Stream` là *luồng nhị phân 0 và 1*.
>    - **Trong ngữ cảnh dự án**: Thay vì biến từng object Java thành chuỗi SQL `INSERT INTO (...) VALUES (...)` (tốn CPU parse cú pháp), Java mở 1 đường ống nhị phân trực tiếp vào Socket của PostgreSQL và đẩy hàng loạt byte nhị phân thô vào bảng.
>    - **Cách liên tưởng**: *"Bơm nước bằng vòi rồng cứu hỏa (Direct COPY) thay vì múc từng gáo nước đổ vào bể (JDBC INSERT)"*.

---

## 5. Luồng con B: Set-based Diff & Parallel Java Analyzer

```mermaid
flowchart TD
    STAGE[("scan_inventory_stage<br/>(1M files vừa quét)")]
    INV[("scan_file_inventory<br/>(Dữ liệu quét trước)")]
    
    STAGE & INV --> SQL_DIFF["SQL Set-based Diff<br/>(Trừ tập hợp trên DB)"]
    
    SQL_DIFF --> DIFF_TABLE[("scan_inventory_diff_stage<br/>(Chỉ chứa file MỚI / ĐỔI)")]
    
    DIFF_TABLE --> CHUNK_READ["Đọc Bounded Chunk<br/>(100.000 items/chunk)"]
    
    CHUNK_READ --> SPLIT["Chia 8 Phân vùng<br/>(~12.500 items/thread)"]
    
    SPLIT --> V1["Virtual Thread #1<br/>(Parse Regex CPU)"]
    SPLIT --> V2["Virtual Thread #2<br/>(Parse Regex CPU)"]
    SPLIT --> V8["Virtual Thread #8<br/>(Parse Regex CPU)"]
    
    V1 & V2 & V8 --> MERGE["Gom kết quả<br/>(ScanChunk Analyzer)"]

    style STAGE fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style INV fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style SQL_DIFF fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style DIFF_TABLE fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style CHUNK_READ fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style SPLIT fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style V1 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style V2 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style V8 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style MERGE fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

### 🔍 Cơ chế kỹ thuật:
1. **Phép trừ tập hợp trên Database**: Thay vì kéo 1M file lên Java để so sánh từng file, Database tự chạy 1 câu SQL so khớp fingerprint (`file_size_bytes` + `modified_at`). Nếu file không đổi $\implies$ Bỏ qua ngay lập tức.
2. **Java 25 Virtual Threads Parallelism**: 8 luồng ảo chạy song song trên CPU phân tích biểu thức chính quy (Regex) và phân loại Video / Comic / Image cho từng chunk 100.000 items (~12.500 items/thread).

> [!TIP]
> ### 💡 Từ điển Thuật ngữ & Mental Model (Set-based & Phân tích song song)
>
> 1. **Set-based (Tư duy xử lý theo khối tập hợp toán học)**:
>    - **Nghĩa tiếng Anh thuần**: `Set` là *tập hợp (trong toán học)*; `based` là *dựa trên / nền tảng trên*.
>    - **Trong ngữ cảnh dự án**: `Set-based` là phong cách ra lệnh cho Database xử lý trọn vẹn cả triệu dòng cùng một lúc như một tập hợp duy nhất bằng 1 câu lệnh SQL (`INSERT ... SELECT ... NOT EXISTS`), tận dụng engine đại số quan hệ tối ưu của PostgreSQL. Ngược lại hoàn toàn với `Row-by-row` (hoặc `Procedural`): kéo dữ liệu lên Java rồi dùng vòng lặp `for-each` để so sánh từng dòng (tốn $> 30\text{s}$).
>    - **Tại sao lại gọi như vậy**: Xuất phát từ *Lý thuyết tập hợp (Set Theory)* của nhà toán học Edgar F. Codd (cha đẻ RDBMS). Dữ liệu trong database là các quan hệ tập hợp, máy tính xử lý phép hợp/giao/trừ tập hợp nhanh gấp hàng trăm lần việc lặp từng phần tử.
>    - **💡 Cách liên tưởng**: *"Thu hoạch lúa bằng máy gặt đập liên hợp (Set-based: gom cả thửa ruộng một lượt) thay vì cầm liềm cắt từng bông lúa một (Row-by-row)"*.
>
> 2. **Partitioning & Deterministic Merge (Chia để trị & Hợp nhất xác định)**:
>    - **Nghĩa tiếng Anh thuần**: `Partition` là *vách ngăn / chia phần*; `Deterministic` là *xác định / luôn ra cùng 1 kết quả không đổi*; `Merge` là *gộp lại*.
>    - **Trong ngữ cảnh dự án**: 100.000 file trong mỗi chunk được chia đều cho 8 Virtual Threads chạy song song (~12.500 items/thread). Khi xong, kết quả được gộp lại theo đúng thứ tự ban đầu để đảm bảo tính nhất quán (Deterministic).
>    - **💡 Cách liên tưởng**: *"Chia bài kiểm tra cho 8 giám khảo cùng chấm điểm (Partitioning), sau đó xếp lại bài theo đúng thứ tự số báo danh (Deterministic Merge)"*.

---

## 6. Luồng con C: Direct COPY Persistence & Atomic Checkpoint

```mermaid
flowchart TD
    ANALYZE["Kết quả phân tích<br/>từ Java (RAM)<br/>(Proposals &amp; Issues)"]
    
    subgraph TX["Transaction Cục bộ<br/>@Transactional(REQUIRES_NEW)"]
        direction TB
        C1["[1] Direct COPY<br/>vào scan_proposal<br/>(Ghi nhị phân)"]
        C2["[2] Direct COPY<br/>vào scan_issue<br/>(Ghi file lỗi/mơ hồ)"]
        C3["[3] Cập nhật<br/>scan_file_inventory<br/>(Cold / Warm Path)"]
        C4["[4] Ghi Checkpoint<br/>+ Lease Fence<br/>vào scan_run"]
        
        C1 --> C2 --> C3 --> C4
    end

    ANALYZE --> TX
    TX --> COMMIT[("Commit DB Chunk<br/>(100.000 items)")]

    style ANALYZE fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style TX fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style C1 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style C2 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style C3 fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style C4 fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
    style COMMIT fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

> [!TIP]
> ### 💡 Từ điển Thuật ngữ & Mental Model (Persistence & Checkpoint)
>
> 1. **Atomic Checkpoint (Điểm kiểm tra nguyên tử / Bất khả phân chia)**:
>    - **Nghĩa tiếng Anh thuần**: `Atomic` là *thuộc về nguyên tử (tính chất không thể chia nhỏ hơn)*; `Checkpoint` là *trạm kiểm soát / điểm mốc lưu trữ*.
>    - **Trong ngữ cảnh dự án**: Một chunk ghi đồng thời cả Proposal, Issue và cập nhật số lượng tiến độ vào `scan_run` trong cùng 1 Transaction `@Transactional(REQUIRES_NEW)`. Nếu mất điện hoặc lỗi giữa chừng, toàn bộ chunk đó bị rollback sạch sẽ 100%, không bị tình trạng "lưu được file nhưng không cập nhật tiến độ".
>    - **💡 Cách liên tưởng**: *"Điểm Save Game tự động: Khi qua màn (chunk), game lưu toàn bộ lượng máu, tiền và trang bị cùng 1 lượt. Không bao giờ có chuyện lưu được tiền mà mất sạch trang bị"*.
>
> 2. **Cold Path vs. Warm Path (Luồng chạy nguội vs. Luồng chạy nóng)**:
>    - **Nghĩa tiếng Anh thuần**: `Cold` là *nguội/lạnh (chưa có gì, mới toanh)*; `Warm` là *nóng/ấm (đã chạy trước đó, đang hoạt động)*; `Path` là *con đường / luồng thực thi*.
>    - **Trong ngữ cảnh dự án**: 
>      - **Cold Path**: Thư mục quét lần đầu tiên (bảng inventory rỗng) $\implies$ Bỏ qua bước kiểm tra UPDATE, nạp thẳng 100% bằng `INSERT` thần tốc.
>      - **Warm Path**: Thư mục đã từng quét (đã có dữ liệu) $\implies$ Phải chạy câu đối soát `UPDATE ... FROM` để so sánh sửa đổi.
>    - **💡 Cách liên tưởng**: *"Làn đường thu phí tự động ETC không dừng (Cold Path: phóng thẳng không cần soát vé) vs. Làn dừng lại soát vé kiểm tra từng xe (Warm Path)"*.

---

## 7. Luồng con D: Background Review Projection & Generation Swap

Sau khi Scan hoàn tất, luồng Review Projection chạy ngầm hoàn toàn độc lập:

```mermaid
flowchart TD
    COMPLETE["Scan hoàn tất 100%<br/>(Status = COMPLETED)"]
    
    COMPLETE --> ENQUEUE["Đẩy 1 Task vào DB:<br/>scan_review_proj_task"]
    
    ENQUEUE --> WORKER["Worker @Scheduled<br/>(Chu kỳ 1s/lần)<br/>Bốc Task xử lý ngầm"]
    
    WORKER --> BUILD["Dựng ngầm Gen 2:<br/>INSERT review_proposal<br/>WHERE gen = 2"]
    
    BUILD --> LOCK["lockRoot(rootKey)<br/>SELECT FOR UPDATE"]
    
    LOCK --> REFRESH["Cập nhật quyết định<br/>đã duyệt từ trước"]
    
    REFRESH --> SWAP["Tráo thế hệ O(1):<br/>UPDATE projection_root<br/>SET current_gen = 2"]
    
    SWAP --> CLEANUP["Dọn rác thế hệ cũ:<br/>DELETE WHERE gen &lt; 2"]
    
    SWAP --> UI["Admin UI xem ngay:<br/>Review Queue Gen 2!"]

    style COMPLETE fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style ENQUEUE fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style WORKER fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style BUILD fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style LOCK fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style REFRESH fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style SWAP fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style CLEANUP fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
    style UI fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
```

> [!TIP]
> ### 💡 Từ điển Thuật ngữ & Mental Model (Review Projection & Generation Swap)
>
> 1. **Generation Swap (Tráo đổi thế hệ $O(1)$) / Blue-Green Switch**:
>    - **Nghĩa tiếng Anh thuần**: `Generation` là *thế hệ (Gen 1, Gen 2)*; `Swap` là *hoán đổi / tráo đổi vị trí*.
>    - **Trong ngữ cảnh dự án**: Thay vì xóa sửa trực tiếp bảng Review đang hiển thị cho Admin (gây giật lag hoặc mất dữ liệu), hệ thống dựng toàn bộ dữ liệu mới ở "Thế hệ 2" ngầm bên dưới. Khi xong xuôi 100%, chỉ cần 1 câu lệnh UPDATE 1 dòng duy nhất (`SET current_generation = 2`) mất $< 1\text{ms}$ để tráo đổi tức thì.
>    - **Tại sao gọi như vậy**: Kỹ thuật này tương đương với mô hình triển khai **Blue-Green Deployment**: Môi trường Blue đang chạy phục vụ khách, môi trường Green dựng sẵn; khi sẵn sàng chỉ cần đổi Router sang Green.
>    - **Cách liên tưởng**: *"Gạt công tắc chuyển nguồn điện: Lắp sẵn dàn bóng đèn mới (Gen 2), khi xong chỉ cần gạt cầu dao sang nguồn mới trong 1 phần nghìn giây"*.
>
> 2. **Pessimistic Lock / `SELECT ... FOR UPDATE` (Khóa bi quan độc quyền)**:
>    - **Nghĩa tiếng Anh thuần**: `Pessimistic` là *bi quan (luôn nghĩ điều xấu nhất sẽ xảy ra)*; `Lock` là *ổ khóa bảo vệ*.
>    - **Trong ngữ cảnh dự án**: Hệ thống "bi quan" giả định rằng chắc chắn sẽ có người khác/tiến trình khác nhảy vào tranh chấp dữ liệu của thư mục này. Vì vậy, trước khi đụng vào, nó khóa cứng dòng đó trong DB (`SELECT ... FOR UPDATE`). Bất kỳ ai khác muốn chạm vào đều phải đứng xếp hàng đợi mở khóa.
>    - **Cách liên tưởng**: *"Khóa chốt cửa phòng vệ sinh: Bước vào là khóa chốt trong ngay lập tức (Pessimistic), người bên ngoài nhìn thấy biển 'Đang có người' và phải đứng đợi, không ai xông vào phá đám được"*.

---

## 8. Tổng kết: Dữ liệu chuyển dịch qua các Bảng như thế nào?

```text
[Filesystem 1M files]
       │
       ▼ (Phase 2: COPY Staging)
[scan_inventory_stage] (UNLOGGED)
       │
       ▼ (Phase 3: SQL Set-based Diff)
[scan_inventory_diff_stage] (UNLOGGED - Chỉ chứa file mới/đổi)
       │
       ▼ (Phase 4: Parallel Virtual Threads 10 Chunks x 100k)
[Java Analyzer in RAM]
       │
       ▼ (Phase 5: Direct COPY 10 Transactions)
[scan_proposal] & [scan_issue] & [scan_file_inventory] (Durable Storage)
       │
       ▼ (Phase 6: Hoàn tất Scan Run -> Enqueue Task)
[scan_review_projection_task]
       │
       ▼ (Luồng con D: Background Worker Rebuild & Swap)
[scan_review_proposal] (Read Model CQRS hiển thị cho Admin UI)
```


👉 **Tổng thời gian toàn bộ tiến trình**: $1,8\text{s} + 0,4\text{s} + 18,5\text{s} + 4,2\text{s} \approx \mathbf{24,9\text{ giây}}$ (Hoàn thành xuất sắc mục tiêu $< 30\text{s}$ cho 1.000.000 files!).
