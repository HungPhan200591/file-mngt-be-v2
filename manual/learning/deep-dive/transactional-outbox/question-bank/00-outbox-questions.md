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
