# 🧭 Deep-Dive: Toàn cảnh Kiến trúc & Data Pipeline Scan Preview 1.000.000 Files (< 30s)

> **Mục tiêu tài liệu**: Bóc tách toàn diện từ First Principles kiến trúc, luồng dữ liệu (Data Pipeline), trục xương sống (Backbone), mô hình xử lý Đồng bộ/Bất đồng bộ (Sync/Async) và các nhánh rẽ phụ (Side-Branches) của tiến trình Scan Preview trong `scan-service`.  
> **Áp dụng dự án**: `file_mngt_microservice` (PostgreSQL 17 / Java 25 Virtual Threads / Workload SC-01).

---

## 1. Bản đồ Cấp 1 (Vĩ mô): Trục xương sống & 4 Nhánh rẽ phụ

Toàn bộ pipeline Scan Preview được điều phối dựa trên **1 Trục xương sống chính (Backbone 6 Phase)** và **4 Nhánh rẽ phụ (Ancillary Lanes)**:

![Trục xương sống Backbone và 4 Nhánh rẽ phụ](./assets/deep-dive-scan-preview-pipeline-under-30s/01-backbone-and-ancillary-lanes.drawio.svg)

*(💡 Gợi ý: Bạn có thể click đúp vào file [assets/deep-dive-scan-preview-pipeline-under-30s/01-backbone-and-ancillary-lanes.drawio.svg](./assets/deep-dive-scan-preview-pipeline-under-30s/01-backbone-and-ancillary-lanes.drawio.svg) trong IntelliJ để chỉnh sửa kéo thả trực quan).*

### 📋 Bảng tra cứu các Chặng trên Trục xương sống:

| Chặng | Tên giai đoạn | Trách nhiệm kỹ thuật cốt lõi | Thời gian thực thi | Luồng con chi tiết |
| :--- | :--- | :--- | :---: | :---: |
| **Phase 1** | Cấp Lease Fencing | Khóa phân vùng `rootKey`, chặn 2 scan chạy đè lên nhau | $\sim 5\text{ms}$ | Mục 3 |
| **Phase 2** | Discovery & Stream COPY | `Files.walkFileTree` $\to$ Bounded Queue $\to$ Direct COPY 2 segments (500k/segment) vào bảng UNLOGGED | $\sim 1,8\text{s} - 6,0\text{s}$ | **Sub-flow A (Mục 4)** |
| **Phase 3** | Set-based Diff | 1 câu SQL `INSERT ... SELECT` lọc các file mới/đổi vào `scan_inventory_diff_stage` | $\sim 0,4\text{s} - 6,5\text{s}$ | **Sub-flow B (Mục 5)** |
| **Phase 4** | Parallel Analyzer | Đọc từng page Keyset 25.000 items, chia 8 partition trên Java Virtual Threads (~3.125 items/thread) | $\sim 8,5\text{s} - 18,5\text{s}$ | **Sub-flow B (Mục 5)** |
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

![Bản đồ Vi phẫu Sync Async Parallel](./assets/deep-dive-scan-preview-pipeline-under-30s/02-sync-async-parallel-model.drawio.svg)

*(💡 Gợi ý: Bạn có thể click đúp vào file [assets/deep-dive-scan-preview-pipeline-under-30s/02-sync-async-parallel-model.drawio.svg](./assets/deep-dive-scan-preview-pipeline-under-30s/02-sync-async-parallel-model.drawio.svg) trong IntelliJ để chỉnh sửa kéo thả trực quan).*

### 📊 Bảng phân tích chi tiết cơ chế Sync / Async / Concurrent:

| Chặng xử lý | Chi tiết hoạt động | Cơ chế thực thi | Thời gian |
| :--- | :--- | :---: | :---: |
| **1. HTTP Controller** | Kiểm tra Snapshot Catalog + Cấp Lease Fencing + Trả HTTP `202`. | **SYNC** (Chặn request client $< 10\text{ms}$) | $< 10\text{ms}$ |
| **2. Discovery Phase** | **Thread 1 (Walker)** quét đĩa đẩy vào Queue; **Thread 2 (COPY)** rút Queue ghi PostgreSQL `COPY`. | **ASYNC** giữa Đĩa và Database (Tách rời I/O qua Queue) | $\sim 1,8\text{s} - 6,0\text{s}$ (1M files) |
| **3. Set-based Diff** | Gửi 1 câu SQL `INSERT ... SELECT` sang PostgreSQL so sánh fingerprint. | **SYNC** (Luồng Worker đợi DB trả kết quả) | $\sim 0,4\text{s} - 6,5\text{s}$ |
| **4. Java Analyzer** | Đọc Keyset 25k items, chia 8 phân vùng chạy song song trên **8 Virtual Threads (Java 25)**. | **PARALLEL / CONCURRENT** (Đa nhân CPU) | $\sim 8,5\text{s} - 18,5\text{s}$ |
| **- Catalog Existence** | Từng Virtual Thread gọi HTTP sang Catalog theo micro-batch 500. | **SYNC** bên trong từng Virtual Thread | $\sim 15\text{ms}$/batch |
| **- Join Barrier** | Luồng Worker chính đứng đợi cả 8 Virtual Threads hoàn tất. | **SYNC BARRIER** (Structured Concurrency) | Điểm chốt chặn |
| **5. Commit DB Chunk** | Mở Transaction ghi `scan_proposal`, `scan_issue`, cập nhật `scan_run`. | **SYNC** (Local Transaction `@Transactional`) | $\sim 1,5\text{s}$/page (25k items) |
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
>    - **Trong ngữ cảnh dự án**: Khi cần phân tích 25.000 file trong 1 page, luồng chính chia làm 8 phần (~3.125 items/thread) và mở 8 Virtual Threads chạy song song. Luồng chính đứng đợi ở "Join Barrier" (chốt điểm danh); khi cả 8 luồng báo cáo hoàn tất thì mới cùng nhau bước tiếp sang bước Commit DB.
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

![Sub-flow A Discovery Stream COPY](./assets/deep-dive-scan-preview-pipeline-under-30s/03-subflow-a-discovery-stream-copy.drawio.svg)

*(💡 Gợi ý: Bạn có thể click đúp vào file [assets/deep-dive-scan-preview-pipeline-under-30s/03-subflow-a-discovery-stream-copy.drawio.svg](./assets/deep-dive-scan-preview-pipeline-under-30s/03-subflow-a-discovery-stream-copy.drawio.svg) trong IntelliJ để chỉnh sửa kéo thả trực quan).*

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

![Sub-flow B Diff and Parallel Analyzer](./assets/deep-dive-scan-preview-pipeline-under-30s/04-subflow-b-diff-parallel-analyzer.drawio.svg)

*(💡 Gợi ý: Bạn có thể click đúp vào file [assets/deep-dive-scan-preview-pipeline-under-30s/04-subflow-b-diff-parallel-analyzer.drawio.svg](./assets/deep-dive-scan-preview-pipeline-under-30s/04-subflow-b-diff-parallel-analyzer.drawio.svg) trong IntelliJ để chỉnh sửa kéo thả trực quan).*

### 🔍 Cơ chế kỹ thuật:
1. **Phép trừ tập hợp trên Database**: Thay vì kéo 1M file lên Java để so sánh từng file, Database tự chạy 1 câu SQL so khớp fingerprint (`file_size_bytes` + `modified_at`). Nếu file không đổi $\implies$ Bỏ qua ngay lập tức.
2. **Java 25 Virtual Threads Parallelism**: 8 luồng ảo chạy song song trên CPU phân tích biểu thức chính quy (Regex) và phân loại Video / Comic / Image cho từng page 25.000 items (~3.125 items/thread).

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
>    - **Trong ngữ cảnh dự án**: 25.000 file trong mỗi page được chia đều cho 8 Virtual Threads chạy song song (~3.125 items/thread). Khi xong, kết quả được gộp lại theo đúng thứ tự ban đầu để đảm bảo tính nhất quán (Deterministic).
>    - **💡 Cách liên tưởng**: *"Chia bài kiểm tra cho 8 giám khảo cùng chấm điểm (Partitioning), sau đó xếp lại bài theo đúng thứ tự số báo danh (Deterministic Merge)"*.

---

## 6. Luồng con C: Direct COPY Persistence & Atomic Checkpoint

![Sub-flow C Direct COPY Persistence](./assets/deep-dive-scan-preview-pipeline-under-30s/05-subflow-c-direct-copy-persistence.drawio.svg)

*(💡 Gợi ý: Bạn có thể click đúp vào file [assets/deep-dive-scan-preview-pipeline-under-30s/05-subflow-c-direct-copy-persistence.drawio.svg](./assets/deep-dive-scan-preview-pipeline-under-30s/05-subflow-c-direct-copy-persistence.drawio.svg) trong IntelliJ để chỉnh sửa kéo thả trực quan).*

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

![Sub-flow D Review Projection Swap](./assets/deep-dive-scan-preview-pipeline-under-30s/06-subflow-d-review-projection-swap.drawio.svg)

*(💡 Gợi ý: Bạn có thể click đúp vào file [assets/deep-dive-scan-preview-pipeline-under-30s/06-subflow-d-review-projection-swap.drawio.svg](./assets/deep-dive-scan-preview-pipeline-under-30s/06-subflow-d-review-projection-swap.drawio.svg) trong IntelliJ để chỉnh sửa kéo thả trực quan).*

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
       ▼ (Historical Phase 4 evidence: 10 Chunks x 100k)
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
