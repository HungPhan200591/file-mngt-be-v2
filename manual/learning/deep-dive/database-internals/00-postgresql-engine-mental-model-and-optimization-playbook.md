# 🗄️ Master Deep-Dive: Bản Đồ Nền Tảng DB Engine & Cẩm Nang Giải Pháp Tối Ưu

> **Mục tiêu tài liệu**: Giúp kỹ sư nắm bắt sâu sắc từ First Principles **bản chất lưu trữ dữ liệu và cơ chế vận hành bên trong của RDBMS (PostgreSQL 17)**: Data Pages (8KB), Buffer Pool, MVCC, WAL, B-Tree Indexes. Giải mã tường minh tại sao các giải pháp tối ưu trong dự án Backend V2 (**`COPY` streaming, Keyset Cursor, Hash Anti-Join, UNLOGGED Staging, Logical Sharding, Bounded Chunks**) lại đạt hiệu năng cao, chúng đang **bypass (bỏ qua) nút thắt nào** dưới tầng CSDL, hoàn cảnh sử dụng và các trade-offs (đánh đổi) đi kèm.

---

## 🧭 1. Bản Đồ Tổng Thể Kiến Trúc PostgreSQL Engine

Trước khi tìm hiểu các kỹ thuật tối ưu, hãy nhìn vào **Bản đồ không gian 3 tầng (Query ➔ Memory ➔ Disk)** của PostgreSQL:

```mermaid
flowchart LR
    subgraph QUERY_LAYER["1. TẦNG XỬ LÝ TRUY VẤN"]
        direction TB
        SQL["Client SQL Query<br/>(SELECT/INSERT/COPY)"] --> PARSER["Parser &amp; Rewriter<br/>(Cây cú pháp AST)"]
        PARSER --> PLANNER["Query Optimizer<br/>(Execution Plan)"]
        PLANNER --> EXECUTOR["Query Executor<br/>(Join/Scan Engine)"]
    end

    subgraph MEM_LAYER["2. TẦNG BỘ NHỚ RAM (Shared Buffers)"]
        direction TB
        WORK_MEM["work_mem<br/>(Hash / Sort trong RAM)"]
        BUFFER_POOL["Buffer Pool<br/>(Data Pages 8KB)"]
        WAL_BUFFERS["WAL Buffers<br/>(Hàng đợi nhật ký ghi)"]
    end

    subgraph DISK_LAYER["3. TẦNG LƯU TRỮ ĐĨA (Storage)"]
        direction TB
        DISK_DATA[("Data Files (.db)<br/>Random I/O")]
        DISK_WAL[("WAL Files (.wal)<br/>Sequential Append")]
    end

    EXECUTOR -->|"1. Nạp/Sửa RAM"| BUFFER_POOL
    EXECUTOR -.->|"Hash Join"| WORK_MEM
    EXECUTOR -->|"2. Log Commit"| WAL_BUFFERS
    WAL_BUFFERS -->|"🚀 Fast fsync"| DISK_WAL
    BUFFER_POOL -.->|"🐢 Checkpoint Flush"| DISK_DATA
    style QUERY_LAYER fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style SQL fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style PARSER fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style PLANNER fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style EXECUTOR fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style MEM_LAYER fill:#004D40,stroke:#fff,stroke-width:2px,color:#fff
    style WORK_MEM fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style BUFFER_POOL fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style WAL_BUFFERS fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style DISK_LAYER fill:#4A148C,stroke:#fff,stroke-width:2px,color:#fff
    style DISK_DATA fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style DISK_WAL fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

---

## 🧱 2. Bóc Tách 4 Khái Niệm Cốt Lõi Dưới Tầng Sâu CSDL

---

### 1️⃣ Data Page (Khối 8KB) & Cấu Trúc Slotted Page
- **Bản chất**: PostgreSQL **không bao giờ đọc/ghi từng byte lẻ trên đĩa**. Đơn vị nhỏ nhất mà CSDL trao đổi với ổ đĩa là một khối **Data Page có kích thước cố định $8\text{ KB}$ (8192 bytes)**.
- **Cơ chế Slotted Page (2 Đầu Co Giãn)**:
  - **Phía trên phát triển xuống $\downarrow$**: `PageHeader (24 bytes)` và mảng `ItemPointers (4 bytes/dòng)` trỏ vào vị trí byte của từng tuple.
  - **Ở giữa**: `Free Space` để chèn thêm dòng mới.
  - **Phía dưới phát triển lên $\uparrow$**: Các `Tuples (Dữ liệu thực tế)` xếp từ đáy trang ngược lên đỉnh.
- **Tuple Header Overhead (~24 bytes/row)**:
  - Mỗi dòng dữ liệu (Tuple) luôn mang theo 24 bytes metadata ẩn:
    - `xmin`: Transaction ID tạo ra dòng này.
    - `xmax`: Transaction ID xóa/sửa dòng này (mặc định $= 0$ nếu đang sống).
    - `t_ctid`: Địa chỉ vật lý của dòng `(Block_Number, Offset)`.

```mermaid
flowchart TD
    subgraph PAGE_8KB["Cấu trúc Slotted Page 8KB trong PostgreSQL"]
        direction TB
        HEADER["PageHeader (24B) ➔ Metadata &amp; LSN mới nhất"]
        PTRS["ItemPointers ➔ [Ptr 1] [Ptr 2] [Ptr 3] (Phát triển xuống ↓)"]
        FREE["─── Free Space (Vùng trống co giãn) ───"]
        TUPLES["Tuples ➔ [Row 3] [Row 2] [Row 1] (Phát triển từ đáy lên ↑)<br/>Mỗi Row gánh Header 24B (xmin, xmax, t_ctid)"]
        HEADER --> PTRS --> FREE --> TUPLES
    end
    style PAGE_8KB fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style HEADER fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style PTRS fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style FREE fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
    style TUPLES fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

---

### 2️⃣ Buffer Pool (Shared Buffers) & Dirty Pages
- **Bản chất**: CSDL là cỗ máy chạy trên RAM. Mọi câu lệnh `SELECT`, `INSERT`, `UPDATE` đều bắt buộc phải đưa Data Page 8KB từ đĩa vào **Buffer Pool trong RAM** trước khi xử lý.
- **Dirty Page là gì?**: Khi bạn chạy lệnh `INSERT` hoặc `UPDATE`:
  1. PostgreSQL tìm trang 8KB trong Buffer Pool.
  2. Sửa trực tiếp byte dữ liệu trên RAM. Trang này lập tức được đánh dấu là **Dirty Page** (Trang dơ — nghĩa là dữ liệu trên RAM đã khác với trên đĩa).
  3. **Không ghi xuống đĩa ngay** (vì Random I/O rất chậm).
  4. Tiến trình chạy ngầm **Checkpointer** sau vài phút mới gom hàng ngàn Dirty Pages để xả (Flush) một lần xuống file dữ liệu trên đĩa.

```mermaid
flowchart LR
    subgraph CLIENT_APP["1. ỨNG DỤNG"]
        REQ["Lệnh UPDATE / INSERT"]
    end

    subgraph BUFFER_POOL["2. RAM: BUFFER POOL (Shared Buffers)"]
        direction TB
        CACHE_CHECK{"Đã có trong RAM?"}
        LOAD_RAM["Nạp Page 8KB từ Đĩa"]
        MUTATE["Sửa Byte trên RAM<br/>➔ DIRTY PAGE"]
        CACHE_CHECK -->|"Miss"| LOAD_RAM --> MUTATE
        CACHE_CHECK -->|"Hit"| MUTATE
    end

    subgraph DISK_STORE["3. ĐĨA CỨNG (Data Files)"]
        direction TB
        CHECKPOINT["Checkpointer Thread<br/>(Gom ngầm sau vài phút)"]
        DISK_PAGES[("Data Files (.db)<br/>Flush hàng ngàn trang")]
        CHECKPOINT --> DISK_PAGES
    end

    REQ --> CACHE_CHECK
    MUTATE -.->|"Không ghi đĩa ngay<br/>(Bypass Random I/O)"| CHECKPOINT
    style CLIENT_APP fill:#1565C0,stroke:#fff,stroke-width:2px,color:#fff
    style REQ fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style BUFFER_POOL fill:#004D40,stroke:#fff,stroke-width:2px,color:#fff
    style CACHE_CHECK fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style LOAD_RAM fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style MUTATE fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style DISK_STORE fill:#4A148C,stroke:#fff,stroke-width:2px,color:#fff
    style CHECKPOINT fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style DISK_PAGES fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
```

---

### 3️⃣ MVCC (Multi-Version Concurrency Control) & Dead Tuples
- **Bản chất của lệnh `UPDATE` trong Postgres**:
  - PostgreSQL **không bao giờ sửa đè lên byte cũ (No In-Place Update)**.
  - Khi `UPDATE dòng X`: Postgres thực chất làm 2 việc:
    1. Đánh dấu dòng cũ là đã chết bằng cách gán `xmax = Transaction_ID_hiện_tại`.
    2. Tạo một dòng hoàn toàn mới (`INSERT`) với `xmin = Transaction_ID_hiện_tại`.
- **Hệ quả (Dead Tuples & Table Bloat)**:
  - Các dòng cũ bị xóa hoặc bị sửa trở thành **Dead Tuples (Dòng rác)** nằm chiếm dung lượng trang 8KB.
  - Tiến trình ngầm **AutoVACUUM** định kỳ phải quét dọn các Dead Tuples này để giải phóng khoảng trống (Free Space) trong trang 8KB cho dòng mới dùng lại.

```mermaid
flowchart LR
    subgraph PHASE1["1. BAN ĐẦU (TxID = 100)"]
        direction TB
        ROW_V1["Tuple V1 (Live)<br/>xmin=100, xmax=0<br/>status = 'PENDING'"]
    end

    subgraph PHASE2["2. SAU UPDATE (TxID = 200)"]
        direction TB
        ROW_DEAD["🛑 Tuple V1 (Dead)<br/>xmin=100, xmax=200<br/>(Rác chiếm 8KB)"]
        ROW_V2["⚡ Tuple V2 (Live)<br/>xmin=200, xmax=0<br/>status = 'APPROVED'"]
        ROW_DEAD -.->|"t_ctid trỏ tới"| ROW_V2
    end

    subgraph PHASE3["3. SAU AUTOVACUUM"]
        direction TB
        FREE_SPACE["🧹 Vùng trống (Free Space)<br/>Tái sinh cho INSERT mới"]
        ROW_LIVE["⚡ Tuple V2 duy nhất<br/>status = 'APPROVED'"]
    end

    PHASE1 --> PHASE2 --> PHASE3
    style PHASE1 fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style ROW_V1 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style PHASE2 fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style ROW_DEAD fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style ROW_V2 fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style PHASE3 fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style FREE_SPACE fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style ROW_LIVE fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

---

### 4️⃣ Write-Ahead Logging (WAL) & Ghi Bền Vững
- **Tại sao cần WAL?**: Vì Dirty Pages chỉ nằm trên RAM và chưa được ghi xuống đĩa ngay, nếu mất điện đột ngột thì toàn bộ thay đổi trên RAM sẽ bốc hơi!
- **Giải pháp**: Trước khi xác nhận `COMMIT` thành công cho ứng dụng, PostgreSQL bắt buộc phải ghi 1 bản ghi nhật ký thay đổi ngắn gọn vào **file WAL (.wal)** và gọi lệnh hệ điều hành `fsync()`.
- **Bí quyết tốc độ**: Ghi WAL là **Sequential I/O (Ghi nối tiếp vào cuối file)** nên cực nhanh ($< 0.5\text{ ms}$), trong khi ghi Data Page là **Random I/O (Tìm trang bất kỳ trên đĩa)** tốn tới $5 - 10\text{ ms}$.

```mermaid
flowchart LR
    subgraph HOT_PATH["🚀 ĐƯỜNG GHI COMMIT NHANH (< 0.5ms)"]
        direction TB
        TX["1. App COMMIT"] --> WAL_BUF["2. Ghi byte nhỏ vào WAL Buffer"]
        WAL_BUF --> WAL_FSYNC["3. Ghi nối đuôi (Sequential)<br/>vào file .wal + fsync()"]
        WAL_FSYNC --> ACK(["4. Trả ACK Thành Công"])
    end

    subgraph SLOW_PATH["🐢 ĐƯỜNG XẢ DATA PAGE CHẬM (Vài Phút Sau)"]
        direction TB
        DIRTY["Dirty Pages 8KB<br/>nằm tạm trên RAM"] --> CHK["Checkpointer gom hàng ngàn trang"]
        CHK --> FLUSH[("Ghi Random I/O<br/>vào Data Files (.db)")]
    end

    TX -.-> DIRTY
    style HOT_PATH fill:#004D40,stroke:#fff,stroke-width:2px,color:#fff
    style TX fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style WAL_BUF fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style WAL_FSYNC fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style ACK fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style SLOW_PATH fill:#4A148C,stroke:#fff,stroke-width:2px,color:#fff
    style DIRTY fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style CHK fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style FLUSH fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
```

---

## 🛠️ 3. Giải Mã 5 Giải Pháp Tối Ưu Trong Dự Án Backend V2

Dưới đây là bảng phân tích sâu: **Giải pháp đó làm gì, bypass (bỏ qua) tầng nào của DB Engine, tại sao lại tạo ra tốc độ đột phá và trade-offs là gì?**

---

### 🚀 Solution 1: PostgreSQL `COPY` Protocol (Thay vì JDBC Batch Insert)

```mermaid
flowchart LR
    subgraph OLD_WAY["🛑 CÁCH TRUYỀN THỐNG: JDBC Batch (14k/s)"]
        direction TB
        A1["Java: Lặp qua từng DTO"] --> A2["SQL Parser &amp; Planner<br/>Parse câu lệnh INSERT"]
        A2 --> A3["Statement Parameter Binding<br/>(statement.setObject...)"]
        A3 --> A4["Ghi từng tuple vào Data Page"]
    end

    subgraph NEW_WAY["🚀 CÁCH TỐI ƯU V2: PostgreSQL COPY (32.5k/s)"]
        direction TB
        B1["Java: Format mảng byte CSV"] --> B2["⚡ BYPASS Parser &amp; Planner<br/>(Bỏ qua hoàn toàn AST)"]
        B2 --> B3["Stream byte trực tiếp vào Page Formatter"]
        B3 --> B4["Nạp hàng loạt Tuple vào Data Page"]
    end

    style OLD_WAY fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style A1 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style A2 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style A3 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style A4 fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style NEW_WAY fill:#004D40,stroke:#fff,stroke-width:2px,color:#fff
    style B1 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style B2 fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style B3 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style B4 fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

- **Bản chất**: Sử dụng giao thức cấp thấp `CopyIn` của PostgreSQL driver để truyền trực tiếp một dòng byte stream thô vào DB engine.
- **Bypass cái gì dưới DB?**:
  - **Bypass tầng SQL Parser & Rewriter**: DB không cần phân tích cú pháp chuỗi SQL `INSERT INTO...`.
  - **Bypass Query Planner**: DB không cần suy nghĩ xem nên chạy plan nào.
  - **Bypass Statement Parameter Binding**: Không tốn CPU map từng tham số dấu `?` trong JDBC.
- **Tại sao nhanh?**: Tốc độ ghi tăng từ **$14.000\text{ records/s}$ lên $\mathbf{32.500\text{ records/s}}$ (Nhanh gấp 2.32 lần)**.
- **Khi nào dùng?**: Khi cần nạp hàng loạt (Bulk Ingestion) từ $\ge 1.000$ bản ghi trở lên (như duyệt 1 triệu proposal trong FT-050).
- **Trade-offs**: Cần format chuỗi chuẩn xác (xử lý escape ký tự, quote chuỗi và NULL), khó bắt lỗi trên từng dòng riêng lẻ nếu một dòng bị sai format.

---

### 📄 Solution 2: Keyset Pagination (Cursor) Thay vì `OFFSET / LIMIT`

```mermaid
flowchart LR
    subgraph OFFSET_WAY["🛑 OFFSET 900.000 (Chậm O(N))"]
        direction TB
        O1["B-Tree Index Scan"] --> O2["Đọc 900k dòng từ Đĩa vào RAM"]
        O2 --> O3["Vứt bỏ 900k dòng vừa đọc<br/>(Lãng phí Disk I/O khổng lồ)"]
        O3 --> O4["Chỉ lấy 25k dòng cuối"]
    end

    subgraph KEYSET_WAY["🚀 KEYSET CURSOR (Nhanh O(log N))"]
        direction TB
        K1["B-Tree Index Seek O(log N)<br/>Nhảy thẳng tới vị trí cursor"]
        K1 --> K2["Đọc đúng 25k dòng liên tiếp"]
        K2 --> K3["Trả kết quả ngay lập tức<br/>(Zero lãng phí Disk I/O)"]
    end

    style OFFSET_WAY fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style O1 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style O2 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style O3 fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style O4 fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style KEYSET_WAY fill:#004D40,stroke:#fff,stroke-width:2px,color:#fff
    style K1 fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style K2 fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style K3 fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

- **Bản chất**: Thay vì dùng `OFFSET 900000`, ta dùng điều kiện lọc theo khóa chính hoặc index đa cột: `WHERE scan_run_id = ? AND id > last_seen_id ORDER BY id LIMIT 25000`.
- **Bypass cái gì dưới DB?**:
  - **Bypass việc quét tuần tự (Sequential Scan) và nạp rác vào Buffer Pool**: Với `OFFSET 900k`, Postgres bắt buộc phải nạp hàng vạn trang 8KB từ đĩa vào RAM chỉ để đếm đủ 900.000 dòng rồi vứt đi.
- **Tại sao nhanh?**: Cây B-Tree thực hiện thao tác **Index Seek $O(\log N)$**, nhảy thẳng đến vị trí con trỏ `last_seen_id` và lấy đúng 25.000 dòng tiếp theo trong vài mili-giây.
- **Khi nào dùng?**: Khi phân trang dữ liệu lớn $> 100.000$ bản ghi, xử lý worker theo batch liên tục (như FT-045, FT-050).
- **Trade-offs**: Không thể nhảy trang tùy ý (không thể nhảy thẳng từ trang 1 sang trang 50); bắt buộc bảng phải có cột sắp xếp đơn điệu và có index hỗ trợ `(scan_run_id, id)`.

---

### 🔍 Solution 3: Hash Anti-Join (`NOT EXISTS`) Thay vì `NOT IN`

```mermaid
flowchart LR
    subgraph STAGE_BUILD["1. BUILD PHASE (RAM work_mem)"]
        direction TB
        B1["Đọc toàn bộ bảng B<br/>(Inventory đã biết)"] --> B2["Tạo Hash Table trong RAM<br/>Key = file_hash"]
    end

    subgraph STAGE_PROBE["2. PROBE PHASE (Quét 1 Lần)"]
        direction TB
        P1["Quét từng dòng bảng A<br/>(Inventory vừa quét)"] --> P2{"Tra cứu Hash Table O(1)"}
        P2 -->|"Tìm thấy"| P3["Bỏ qua (Trùng lặp)"]
        P2 -->|"Không thấy"| P4["⚡ Giữ lại (File Mới / Sửa)"]
    end

    STAGE_BUILD --> STAGE_PROBE
    style STAGE_BUILD fill:#004D40,stroke:#fff,stroke-width:2px,color:#fff
    style B1 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style B2 fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style STAGE_PROBE fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style P1 fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style P2 fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style P3 fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
    style P4 fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

- **Bản chất**: Dùng phép trừ đại số quan hệ $A \setminus B$ bằng cú pháp `WHERE NOT EXISTS (SELECT 1 FROM b WHERE b.key = a.key)`.
- **Bypass cái gì dưới DB?**:
  - **Bypass giải thuật Nested Loop Join $O(N \times M)$**: Không phải lấy từng dòng của bảng A chạy đi quét toàn bộ bảng B.
  - **Bypass vấn đề 3-Valued Logic của `NOT IN`**: `NOT IN` sẽ quét toàn bộ bảng nếu gặp `NULL`, làm vỡ index.
- **Tại sao nhanh?**: Giải thuật chia làm 2 pha chạy hoàn toàn trên RAM `work_mem` với độ phức tạp tuyến tính **$O(N + M)$**, lọc 1 triệu file trong **$< 1\text{ giây}$** (nhanh hơn 10 – 50 lần).
- **Khi nào dùng?**: Dùng khi đối soát dữ liệu (Reconciliation), tìm danh sách file mới, file bị sửa đổi, file bị xóa giữa 2 lần quét (FT-025, FT-048).
- **Trade-offs**: Cần cấp đủ dung lượng RAM `work_mem` (ví dụ `work_mem = 64MB`). Nếu bảng băm lớn hơn `work_mem`, Postgres sẽ phải xả ra đĩa (Spill to Disk) làm giảm tốc độ.

---

### ⚡ Solution 4: Bảng `UNLOGGED` Staging Table

```mermaid
flowchart LR
    subgraph LOGGED_FLOW["1. Bảng thường (Logged Table)"]
        direction TB
        L_RAM["Ghi Dirty Page trong RAM"] --> L_WAL["Ghi WAL + fsync() xuống đĩa"]
        L_WAL --> L_ACK(["Xác nhận Commit"])
    end

    subgraph UNLOGGED_FLOW["2. Bảng UNLOGGED Staging"]
        direction TB
        U_RAM["Ghi Dirty Page trong RAM"] --> U_BYPASS["⚡ BYPASS 100% Ghi WAL &amp; fsync()"]
        U_BYPASS --> U_ACK(["Xác nhận Commit ngay lập tức"])
    end

    style LOGGED_FLOW fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style L_RAM fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style L_WAL fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style L_ACK fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style UNLOGGED_FLOW fill:#004D40,stroke:#fff,stroke-width:2px,color:#fff
    style U_RAM fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style U_BYPASS fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style U_ACK fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

- **Bản chất**: Tạo bảng dữ liệu với từ khóa `CREATE UNLOGGED TABLE scan_inventory_staging (...)`.
- **Bypass cái gì dưới DB?**:
  - **Bypass 100% tầng Write-Ahead Logging (WAL)**: CSDL không ghi bất kỳ byte nhật ký nào vào file `.wal` và không gọi `fsync()`.
- **Tại sao nhanh?**: Tốc độ ghi dữ liệu tạm tăng **gấp 3 – 5 lần**, hoàn toàn giải phóng áp lực I/O đĩa cứng.
- **Khi nào dùng?**: Dùng làm vùng đệm chứa dữ liệu quét thô (Staging Table), tính toán đối soát xong là xóa, hoặc dữ liệu có thể dễ dàng quét lại từ ổ đĩa nếu máy chủ bị sập nguồn (FT-025, FT-031).
- **Trade-offs**: **Không an toàn khi sập nguồn (No Durability)**. Khi PostgreSQL restart sau crash, toàn bộ dữ liệu trong bảng `UNLOGGED` sẽ tự động bị xóa sạch (`TRUNCATE`). Không bao giờ dùng cho dữ liệu chính như đơn hàng, tài khoản, decisions.

---

### 🧩 Solution 5: Bounded Chunking (25k rows) & Logical Sharding (FT-050 / FT-051)

```mermaid
flowchart LR
    subgraph INTAKE["1. INTAKE"]
        direction TB
        REQ["1.000.000 Proposals"] --> HASH["Hash Sharding<br/>mod(hash(id), 4)"]
    end

    subgraph WORKERS["2. 4 LOGICAL SHARD WORKERS (Virtual Threads)"]
        direction TB
        S0["Shard 0: 25k Chunks ➔ COPY"]
        S1["Shard 1: 25k Chunks ➔ COPY"]
        S2["Shard 2: 25k Chunks ➔ COPY"]
        S3["Shard 3: 25k Chunks ➔ COPY"]
    end

    subgraph CONVERGE["3. HỘI TỤ (Commit)"]
        direction TB
        AGG{"Tất cả Shards<br/>COMPLETED?"} --> COMMIT(["APPROVAL_COMMITTED<br/>(30.8 giây cho 1M files)"])
    end

    HASH --> S0 & S1 & S2 & S3
    S0 & S1 & S2 & S3 --> AGG
    style INTAKE fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style REQ fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style HASH fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style WORKERS fill:#004D40,stroke:#fff,stroke-width:2px,color:#fff
    style S0 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style S1 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style S2 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style S3 fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style CONVERGE fill:#4A148C,stroke:#fff,stroke-width:2px,color:#fff
    style AGG fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style COMMIT fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

- **Bản chất**: Chia 1 triệu proposal thành 40 chunks nhỏ (25.000 rows/chunk), mỗi chunk commit trong 1 transaction `REQUIRES_NEW` độc lập. Dùng `mod(abs(hashtext(id)), 4)` để 4 Shard Workers ghi song song qua `FOR UPDATE SKIP LOCKED`.
- **Bypass cái gì dưới DB?**:
  - **Bypass tràn Buffer Pool & OOM**: Tránh việc 1 transaction khổng lồ tích tụ 1.000.000 dirty tuples trong RAM làm tràn Shared Buffers.
  - **Bypass Single-Writer Bottleneck**: Phá vỡ nút thắt 1 kết nối ghi tuần tự bằng cách mở 4 luồng ghi song song trên DB đa nhân.
  - **Bypass Long-Running Transaction Lock**: Tránh giữ khóa bảng quá lâu làm nghẽn các câu lệnh khác.
- **Tại sao nhanh?**: Rút ngắn thời gian duyệt 1M files từ **$121\text{s} \rightarrow \mathbf{30,8\text{s}}$ (Thông lượng đạt $\mathbf{32.500\text{ records/s}}$)**.
- **Khi nào dùng?**: Xử lý hàng loạt quy mô lớn ($> 100.000$ đến hàng triệu bản ghi).
- **Trade-offs**: Cần bảng Shard Ledger (`scan_approval_operation_shard`) để theo dõi trạng thái con trỏ và xử lý retry khi 1 shard bị crash.

---

## 📊 4. Ma Trận Quyết Định Cho Kỹ Sư (Architect Decision Matrix)

| Vấn đề gặp phải | Giải pháp áp dụng | Tầng DB được tối ưu | Điểm nghẽn được Bypass | Trade-off phải chấp nhận |
| :--- | :--- | :--- | :--- | :--- |
| **Ghi hàng loạt quá chậm** | **PostgreSQL `COPY`** | Network & Parser | Bypass SQL Parser, AST & Parameter Binding | Khó validate từng dòng riêng lẻ |
| **Phân trang trang sau bị lag** | **Keyset Pagination** | Storage Engine | Bypass nạp hàng triệu Data Pages rác vào RAM | Không nhảy trang tùy ý được |
| **Lọc so sánh 2 bảng triệu dòng** | **Hash Anti-Join (`NOT EXISTS`)** | Memory Execution | Bypass Nested Loop $O(N \times M)$ & 3-valued NULL logic | Tốn bộ nhớ `work_mem` |
| **Nạp dữ liệu tạm làm chậm hệ thống** | **`UNLOGGED` Table** | Durability (WAL) | Bypass 100% việc ghi file WAL và `fsync` đĩa | Mất trắng dữ liệu tạm khi CSDL crash |
| **Ghi 1M bản ghi làm treo CSDL** | **Bounded Chunking & Sharding** | Concurrency & Buffer | Bypass Long Transaction & Single-thread WAL lock | Phải quản lý Checkpoint & Retry logic |

---

## 🔗 Liên Kết Học Tập Liên Quan:
- [Bài 01: Write-Ahead Logging & Storage Engine Internals](./01-wal-and-storage-engine-internals.md)
- [Bài 02: Anti-Join & Hash Anti-Join Query Optimization](./02-anti-join-and-query-optimization.md)
- [Bài 03: UNLOGGED Tables & Transient Storage](./03-unlogged-tables-and-transient-storage.md)
- [Bài 04: Giới Hạn Vật Lý RDBMS vs 1M+ Records/s](./04-rdbms-limits-vs-million-records-per-second.md)
- [Bài 05: Bản Chất & Cơ Chế Thực Hiện ACID](./05-acid-internals-and-implementation-mechanisms.md)
