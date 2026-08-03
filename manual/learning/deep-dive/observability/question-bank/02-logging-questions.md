# ❓ Logging & ELK Stack — Interview Question Bank

Bộ câu hỏi phỏng vấn Chuyên sâu (Senior / Lead) về Structured Logging, Elastic Common Schema (ECS), Logstash Ingestion Pipeline và Kibana Discovery.

---

## 📊 Bảng Ma trận Coverage

| Level | Foundation | Senior | Architect | Tổng số câu |
| :--- | :---: | :---: | :---: | :---: |
| **Số lượng** | 1 | 1 | 1 | 3 |

---

## 🎯 Danh sách Câu hỏi Chi tiết & Đáp án Chuẩn

### OBS-LOG-001 — `SENIOR`
**Question:** Tại sao Logging trong dự án lại dùng cơ chế Ghi File ECS JSON local + Logstash Ship (Decoupled File Shipping) thay vì cho Microservice trực tiếp đẩy Log qua Network (Socket/HTTP Appender) về Logstash/Elasticsearch?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Hiểu biết về rủi ro Cascading Failure, hiệu năng I/O của Operating System và nguyên tắc Decoupling hạ tầng logging.<br>
**Answer outline:**
- **Phòng tránh Sập Dây Chuyền (Cascading Failure)**: Nếu Logstash/Elasticsearch gặp sự cố (High CPU, ngắt kết nối mạng, nghẽn I/O, hoặc Disk Full), việc ứng dụng gửi log đồng bộ/bất đồng bộ qua Network Appender sẽ nhanh chóng làm tràn bộ nhớ đệm (Buffer Overflow), tắc nghẽn Worker Thread Pool và làm **treo toàn bộ API nghiệp vụ**.
- **Hiệu năng OS Page Cache cực cao**: Thao tác ghi log ra đĩa local (`/logs/*.json`) sử dụng OS Buffered Writes trong RAM Kernel, thời gian phản hồi ở mức microsecond (µs), tiệm cận bằng 0ms.
- **Tính Độc lập (Decoupled Architecture)**: Container Logstash chạy hoàn toàn tách biệt, chủ động tail file và ship log ngầm theo nhịp độ của nó. Nếu Logstash sập, log chỉ tạm tích lũy trên ổ đĩa local. Khi Logstash sống lại, nó tiếp tục đọc từ vị trí cũ (Offset) mà không mất mát dữ liệu và không ảnh hưởng đến ứng dụng.<br>
**Required trade-offs:** Cần cơ chế xoay vòng file (Log Rotation) để không làm đầy đĩa cứng local.<br>
**Follow-up ladder:** Filebeat khác Logstash ở điểm nào? Khi nào nên dùng Filebeat làm log shipper ở edge layer?<br>
**Red flags:** Cho rằng "Ghi file chậm hơn gửi qua Network Socket".

---

### OBS-LOG-002 — `FOUNDATION`
**Question:** Spring Boot 4 Built-in Structured Logging chuẩn Elastic Common Schema (ECS) hoạt động như thế nào và có ưu điểm gì so với cấu hình Logback/Log4j2 XML truyền thống?<br>
**Target depth:** `D1-D2` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Cập nhật kiến thức mới của Spring Boot Framework và Elastic Common Schema (ECS) standard.<br>
**Answer outline:**
- **Spring Boot 4 Built-in Feature**: Chỉ cần khai báo `logging.structured.format.file=ecs` trong `application.properties`, Spring Boot tự động định dạng mọi log output ra dạng JSON chuẩn ECS mà không cần thêm phụ thuộc thư viện logstash-logback-encoder ngoài.
- **Chuẩn hóa Elastic Common Schema (ECS)**:
  - Tất cả các trường cơ bản được đặt tên thống nhất trên mọi microservice: `@timestamp`, `log.level`, `service.name`, `process.thread.name`, `log.logger`, `message`, `correlationId`.
  - Giúp Elasticsearch tự động map đúng Data Types (keyword, date, text) mà không cần viết custom Grok filter phức tạp ở Logstash.
- **So với Logback XML cũ**: Không còn tình trạng mỗi lập trình viên tự format log text một kiểu (`2026-08-03 INFO [scan-service] [main] ...`), giúp việc query và parse trên Kibana đạt hiệu năng cao nhất.<br>
**Required trade-offs:** Log định dạng JSON đọc bằng mắt thường trên console hơi rối hơn text truyền thống (do đó console vẫn giữ định dạng ansi text, chỉ file mới format ECS JSON).<br>
**Follow-up ladder:** Làm thế nào để thêm custom key-value vào cấu trúc ECS JSON log trong Java?<br>
**Red flags:** Tự viết regex/grok filter để parse log text không cấu hình.

---

### OBS-LOG-003 — `ARCHITECT`
**Question:** Tại sao dữ liệu Log Data Stream (`logs-file_mngt_v2-*`) và Dữ liệu Tìm kiếm Media (`media-subject-search`) trong Elasticsearch phải được phân tách thành 2 Index hoàn toàn độc lập?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Tư duy thiết kế Elasticsearch Data Architecture, cách phân tách Workload (Log Ingestion vs Search Query).<br>
**Answer outline:**
1. **Phân tách Đặc tính Workload (Workload Isolation)**:
   - **Log Data Stream**: Là dữ liệu Append-Only ghi liên tục theo thời gian, tần suất ghi (Write Heavy) cực cao, hiếm khi update hay delete. Phù hợp sử dụng **Elasticsearch Data Streams** với lifecycle tự động (Rollover/Delete old logs).
   - **Media Search Index**: Là dữ liệu nghiệp vụ (Domain Data) phục vụ người dùng tìm kiếm (`query-service`), tần suất đọc (Read Heavy) cao, có thao tác update document khi thông tin media thay đổi.
2. **Bảo vệ Tính Sẵn sàng & Hiệu năng Nghiệp vụ**:
   - Nếu nghẽn log hoặc lượng log bùng nổ (Log Spike), đĩa hay tài nguyên của Log Data Stream bị ảnh hưởng nhưng **Index tìm kiếm Media vẫn phản hồi nhanh chóng cho người dùng end-user**.
3. **Cấu hình Retention Policy Khác nhau**:
   - Log chỉ cần giữ 7 - 30 ngày (tự động xóa qua ILM - Index Lifecycle Management).
   - Media Search Index cần lưu trữ vĩnh viễn theo cơ sở dữ liệu Postgres `catalog_db`.<br>
**Required trade-offs:** Cần quản lý 2 Index Pattern riêng biệt trên Elasticsearch Cluster.<br>
**Follow-up ladder:** Index Lifecycle Management (ILM) trong Elasticsearch gồm những phase nào?<br>
**Red flags:** Lưu chung Log record và Business Search Entity vào cùng một Elasticsearch Index.
