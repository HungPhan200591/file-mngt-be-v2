# ❓ Transactional Outbox Pattern — Interview Question Bank

Bộ câu hỏi phỏng vấn Chuyên sâu (Senior / Solution Architect) về Transactional Outbox Pattern, Dual-Write Problem, Idempotent Consumer, Polling vs CDC và DB Optimization.

---

## 📊 Bảng Ma trận Coverage

| Level | Foundation | Senior | Architect | Tổng số câu |
| :--- | :---: | :---: | :---: | :---: |
| **Số lượng** | 1 | 2 | 2 | 5 |

---

## 🎯 Danh sách Câu hỏi Chi tiết & Đáp án Chuẩn

### OUTBOX-001 — `FOUNDATION`
**Question:** Bản chất cốt lõi của bài toán Dual-Write là gì? Tại sao hai thao tác ghi Database và gửi Kafka không thể gộp thành 1 Transaction duy nhất?<br>
**Target depth:** `D1-D2` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Hiểu biết về ranh giới tài nguyên hệ thống, hạn chế của ACID transaction khi vượt khỏi 1 database single node.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Dual-Write không thể tạo 1 ACID Transaction duy nhất vì DB và Kafka dùng 2 Transaction Manager khác nhau (không có 2PC). Outbox Pattern giải quyết bằng cách **gộp Ghi Entity + Ghi Outbox Event thành 1 Local DB ACID Transaction duy nhất**, sau đó dùng **Outbox Relay đọc và gửi ngầm sang Kafka**."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Vấn đề Dual-Write là gì?** ➔ 💡 **Rủi ro mất nhất quán khi ghi vào 2 hệ thống độc lập (Postgres DB + Kafka Broker)**.
- ❓ **Tại sao `@Transactional` không rollback được Kafka send?** ➔ 💡 **Vì Kafka và Postgres không dùng chung 1 Transaction Manager (thiếu 2PC)**.
- ❓ **Tại sao không dùng 2PC (Two-Phase Commit)?** ➔ 💡 **2PC làm tăng độ trễ hàng trăm lần, giảm throughput nặng và gây Deadlock**.
- ❓ **Giải pháp Outbox Pattern hoạt động ra sao?** ➔ 💡 **Gộp Ghi Entity + Ghi Outbox Record trong 1 Local DB Transaction ➔ Outbox Relay gửi ngầm Kafka**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Dual-Write Risk ➔ No 2PC ➔ Local DB ACID Transaction (Entity + Outbox Record) ➔ Outbox Relay Async**.

**Answer outline:**
- **Bản chất Dual-Write**: Là rủi ro mất nhất quán khi ứng dụng phải ghi vào 2 hệ thống lưu trữ độc lập (PostgreSQL RDBMS và Apache Kafka Broker) trong cùng một use case.
- **Không thể tạo 1 ACID Transaction duy nhất vì**: PostgreSQL và Kafka không dùng chung một Transaction Manager. Nếu không có cơ chế 2PC (Two-Phase Commit), nếu bước 1 (save DB) OK nhưng bước 2 (send Kafka) nổ exception ➔ DB đã đổi nhưng Kafka không nhận tin (Mất Event). ngược lại nếu send Kafka OK nhưng commit DB nổ exception ➔ Kafka đã có tin nhưng DB bị rollback (Phantom Event).
- **Tại sao không dùng 2PC (Two-Phase Commit)**: 2PC yêu cầu Distributed Lock xuyên mạng, làm tăng latency hàng trăm lần, giảm throughput nghiêm trọng và gây rủi ro Deadlock cao trong Microservices.
- **Outbox Pattern**: Quy đổi 2PC thành **1 Local ACID DB Transaction** (Entity + Outbox Record) cộng với **1 Async Outbox Relay Process**.<br>
**Required trade-offs:** Chấp nhận tính nhất quán cuối cùng (Eventual Consistency) thay vì Strict Immediate Consistency.<br>
**Follow-up ladder:** Khác biệt giữa Local Transaction và Distributed Transaction (XA/2PC) là gì?<br>
**Red flags:** Cho rằng có thể bọc `@Transactional` của Spring để rollback cả Kafka send call.

---

### OUTBOX-002 — `SENIOR`
**Question:** Nếu Outbox Relay đọc Outbox Table và gửi Kafka thành công, nhưng ứng dụng bị crash trước khi kịp cập nhật trạng thái `PUBLISHED` trong DB thì sao?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Hiểu biết về At-Least-Once Delivery semantics, hiện tượng duplicate event và kỹ thuật Idempotent Consumer.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Bản tin đó sẽ được Outbox Relay đọc và gửi lại lần 2 ➔ Tạo ra **Duplicate Event** (nguyên lý **At-Least-Once Delivery**). Phía Consumer **bắt buộc phải triển khai Idempotent Consumer** bằng bảng `processed_event (event_id PRIMARY KEY)` hoặc kiểm tra `event.version > db.version` để xử lý trùng lặp an toàn."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Hiện tượng gì xảy ra khi Outbox Relay sập trước khi update `PUBLISHED`?** ➔ 💡 **Gửi lại bản tin ở chu kỳ sau ➔ Duplicate Event (At-Least-Once Delivery)**.
- ❓ **Outbox Pattern cam kết ngữ nghĩa giao tin nào?** ➔ 💡 **At-Least-Once Delivery (Không bao giờ mất tin, nhưng có thể bị trùng)**.
- ❓ **Cách xử lý trùng lặp phía Consumer?** ➔ 💡 **Idempotent Consumer với bảng `processed_event (event_id PK)` hoặc Version check**.
- 🔑 **Keyword cốt lõi cần nhớ**: **At-Least-Once Delivery ➔ Duplicate Event Risk ➔ Idempotent Consumer (`processed_event` PK / Version Check)**.

**Answer outline:**
- **Kịch bản trùng lặp**: Ở lần quét tiếp theo của Outbox Relay, bản tin đó vẫn mang trạng thái `PENDING` nên sẽ được đọc và gửi lại sang Kafka ➔ Tạo ra **Duplicate Event**.
- **Nguyên lý At-Least-Once Delivery**: Outbox Pattern đảm bảo bản tin không bao giờ bị mất, nhưng có thể bị giao lặp lại từ 1 lần trở lên.
- **Giải pháp Idempotent Consumer bắt buộc**:
  1. Phía Consumer (`catalog-service` / `query-service`) sử dụng bảng DB `processed_event (event_id PRIMARY KEY)` để lưu vết các `eventId` đã xử lý thành công trong cùng transaction nghiệp vụ.
  2. Nếu nhận được event có `eventId` đã tồn tại trong `processed_event` ➔ Bỏ qua không xử lý lại.
  3. Sử dụng **State-Based Event (Snapshot)** kết hợp kiểm tra `subjectVersion` (`if event.version > db.version`) để đảm bảo idempotency tuyệt đối.<br>
**Required trade-offs:** Phải chấp nhận tốn bộ nhớ lưu trữ bảng `processed_event` ở phía Consumer.<br>
**Follow-up ladder:** Làm thế nào để dọn dẹp bảng `processed_event` cũ mà không bị lặp lại các event rất cũ?<br>
**Red flags:** Trả lời "Dùng Outbox Pattern là đảm bảo Exactly-Once 100%, không bao giờ bị trùng event".

---

### OUTBOX-003 — `SENIOR`
**Question:** So sánh hai cơ chế Outbox Relay: Polling Publisher (Dùng SQL `@Scheduled`) vs CDC (Change Data Capture như Debezium/Kafka Connect)? Tại sao dự án chọn Polling?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Khả năng đánh giá trade-off giữa độ phức tạp hạ tầng và hiệu năng vận hành.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"**Polling** thuần Java SQL đơn giản, dễ test local/CI-CD nhưng có trễ polling. **CDC Debezium** đọc Postgres WAL log tiệm cận Zero-latency nhưng hạ tầng phức tạp (Kafka Connect/Debezium). Dự án chọn Polling vì **đơn giản hóa môi trường Dev/Testing mà vẫn đảm bảo At-Least-Once 100%**."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Ưu/Nhược điểm của Polling Publisher?** ➔ 💡 **Thuần Java SQL đơn giản, dễ test ➔ Nhược điểm: Có độ trễ polling (500ms) & tải SELECT query**.
- ❓ **Ưu/Nhược điểm của CDC Debezium?** ➔ 💡 **Đọc Postgres WAL tiệm cận Zero-latency ➔ Nhược điểm: Phụ thuộc hạ tầng Kafka Connect phức tạp**.
- ❓ **Tại sao Backend V2 lại chọn Polling?** ➔ 💡 **Nguyên tắc Pragmatic (Thiết thực): Đơn giản hóa Dev Local / Testing Harness nhưng vẫn chuẩn 100% At-Least-Once**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Polling (Thuần Java SQL / Đơn giản) vs CDC Debezium (Postgres WAL / Zero-Latency / Phức tạp hạ tầng)**.

**Answer outline:**
- **Polling Publisher (SQL Scheduled)**:
  - *Ưu điểm*: Cực kỳ đơn giản, thuần code Java + SQL table, không phụ thuộc hạ tầng ngoài, chạy mượt mà ở môi trường Dev, Local, CI/CD và Testcontainers.
  - *Nhược điểm*: Tạo tải Polling SQL DB (`SELECT ... WHERE status = 'PENDING'`) và có độ trễ nhỏ do polling interval (500ms - 1s).
- **CDC Debezium (Change Data Capture)**:
  - *Ưu điểm*: Đọc trực tiếp Postgres Write-Ahead Log (WAL), tiệm cận Zero-latency, không tạo tải SQL Query cho DB.
  - *Nhược điểm*: Phụ thuộc vào Kafka Connect, Debezium plugin, bắt buộc cấu hình Postgres `wal_level = logical` phức tạp.
- **Lý do dự án chọn Polling**:
  - Tuân thủ nguyên tắc **Pragmatic & Fit-for-purpose**. Tối ưu hóa trải nghiệm lập trình local dev và testing harness mà vẫn đáp ứng 100% yêu cầu At-Least-Once Delivery.<br>
**Required trade-offs:** Chấp nhận độ trễ polling vài trăm milisecond đổi lấy sự đơn giản hạ tầng.<br>
**Follow-up ladder:** Trong môi trường Production với 50.000 QPS, bạn sẽ chuyển sang Debezium như thế nào?<br>
**Red flags:** Cài đặt Debezium/Kafka Connect ngay từ phase đầu dev local làm phức tạp hệ thống mà không có nhu cầu tải lớn.

---

### OUTBOX-004 — `ARCHITECT`
**Question:** Nếu bảng Outbox tích tụ hàng triệu bản tin (Backlog Spike) do Kafka sập vài giờ, làm sao để dọn dẹp và duy trì hiệu năng SQL Query của Outbox Relay?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Khả năng tối ưu hóa Database PostgreSQL dưới áp lực tải lớn (Table Bloat, Index Tuning, Partitioning).<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Dùng **Postgres Partial Index** `WHERE status = 'PENDING'` để câu `SELECT` luôn đạt $O(\log N)$ bất kể hàng triệu tin `PUBLISHED`, kết hợp **Outbox Cleanup Worker** xóa batch record cũ quá 24h và **Table Partitioning theo ngày** để `DROP TABLE` giải phóng đĩa siêu tốc."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Kỹ thuật quan trọng nhất giữ tốc độ Query Outbox khi bảng có triệu bản tin?** ➔ 💡 **Partial Index (`WHERE status = 'PENDING'`)**.
- ❓ **Tại sao Partial Index lại siêu nhẹ?** ➔ 💡 **Chỉ index các dòng `PENDING` ít ỏi, hoàn toàn bỏ qua các dòng `PUBLISHED` đã xử lý**.
- ❓ **Dọn dẹp bảng Outbox thế nào để không nổ Table Bloat?** ➔ 💡 **Cleanup Worker xóa batch nhỏ hoặc Range Partitioning theo ngày (`DROP TABLE`)**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Partial Index (`WHERE status = 'PENDING'`) ➔ Batch Cleanup ➔ Range Partitioning DROP TABLE**.

**Answer outline:**
1. **Partial Indexing (Chỉ mục một phần)**:
   - Tạo B-Tree Partial Index: `CREATE INDEX idx_outbox_pending ON outbox_table (status, created_at) WHERE status = 'PENDING';`
   - Nhờ cờ `WHERE status = 'PENDING'`, dung lượng Index cực nhỏ và không bị phình to khi có hàng triệu bản tin `PUBLISHED`. Câu lệnh `SELECT` luôn đạt tốc độ tối đa $O(\log N)$.
2. **Outbox Cleanup Worker**:
   - Chạy job ngầm xóa các record `PUBLISHED` cũ quá 24h-7 ngày bằng câu lệnh batch `DELETE ... LIMIT 1000`.
3. **Table Partitioning & Truncation (High-Throughput)**:
   - Sử dụng Range Partitioning theo ngày (`outbox_y2026m08d01`). Cuối ngày chỉ cần `DROP TABLE` partition cũ để giải phóng dung lượng đĩa lập tức mà không gây lock bảng chính.<br>
**Required trade-offs:** Partial Index yêu cầu câu truy vấn SQL phải khớp chính xác điều kiện `WHERE status = 'PENDING'`.<br>
**Follow-up ladder:** Tại sao câu lệnh `DELETE` lớn trong PostgreSQL lại gây ra Table Bloat (MVCC dead tuples)?<br>
**Red flags:** Chạy `DELETE FROM outbox_table` toàn bộ bảng lớn mà không batching gây lock table.

---

### OUTBOX-005 — `ARCHITECT`
**Question:** Event trong Outbox Pattern nên thiết kế theo dạng State-based Event (Snapshot) hay Fine-grained Event (Delta)? Phân tích ưu nhược điểm trong dự án này?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `MEDIUM` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Tư duy thiết kế Event Schema, tính độc lập của Consumer và khả năng xử lý out-of-order events.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Dự án chọn **State-based Event (Snapshot)** để Consumer chỉ việc **Upsert/Reconcile** dữ liệu mới nhất mà **không bị phụ thuộc thứ tự tuyệt đối của Event (Out-of-order Events)**, xử lý Retry và Deduplication cực kỳ đơn giản."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Dự án chọn kiểu thiết kế Event nào?** ➔ 💡 **State-based Event (Snapshot chứa trọn vẹn state mới nhất)**.
- ❓ **Ưu điểm lớn nhất của State-based Event?** ➔ 💡 **Consumer không sợ Event bị xáo trộn thứ tự (Out-of-order), chỉ cần Reconcile & Check Version**.
- ❓ **Nhược điểm của State-based Event?** ➔ 💡 **Dung lượng Payload bản tin lớn hơn một chút so với Delta Event**.
- 🔑 **Keyword cốt lõi cần nhớ**: **State-based Event (Snapshot) ➔ Upsert/Reconcile Logic ➔ Chống Out-of-Order Event ➔ Consumer Bền Bỉ**.

**Answer outline:**
- **Dự án chọn State-Based Event (Snapshot)** (`media.subject.changed.v1` chứa trọn vẹn thông tin Subject & danh sách Assets mới nhất).
- **Ưu điểm của State-Based Event**:
  - Consumer (`query-service`) chỉ cần thực hiện Upsert/Reconcile snapshot mới nhất vào Read Model.
  - Không phụ thuộc vào thứ tự tuyệt đối của hàng loạt event nhỏ. Nếu Event A và Event B bị đảo thứ tự trên Kafka, Consumer chỉ cần kiểm tra `subjectVersion` để giữ phiên bản mới nhất.
  - Xử lý Retry và Deduplicate cực kỳ đơn giản.
- **Nhược điểm**:
  - Dung lượng Payload bản tin lớn hơn so với Delta event (nhưng hoàn toàn chấp nhận được với media metadata JSON nhẹ).<br>
**Required trade-offs:** Payload dung lượng lớn hơn nhưng đổi lại Consumer logic cực kỳ bền bỉ và đơn giản.<br>
**Follow-up ladder:** Khi nào thì Fine-grained Delta Event là bắt buộc (ví dụ: Event Sourcing / Financial Ledger)?<br>
**Red flags:** Dùng Delta event nhưng không xử lý vấn đề event bị tới lệch thứ tự (Out-of-order Events).
