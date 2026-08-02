# 📖 Từ vựng Kỹ thuật & Nghiệp vụ Dự án (Technical Glossary)

Tài liệu tra cứu nhanh các thuật ngữ tiếng Anh chuyên ngành (Technical Terms), Mô hình Kiến trúc (Architectural Patterns) và Khái niệm Nghiệp vụ (Domain Concepts) được sử dụng trong dự án **Backend V2** (`file_mngt_microservice`). Kèm theo **phiên âm đọc kiểu tiếng Việt thuần** giúp đọc dễ dàng, chuẩn xác.

---

## 🎯 1. Mô hình & Khái niệm Kiến trúc Lõi (Core Architectural Terms)

### 🔹 Canonical (Canonical Data / Canonical Model)
- **🗣️ Cách đọc (Phát âm)**: *Ca-no-ni-cồ* (hoặc *Kơ-no-ni-cồ*)
- **Giải thích tiếng Việt**: *Dữ liệu chuẩn hóa / Nguồn sự thật duy nhất (Single Source of Truth)*.
- **Trong dự án**: `catalog-service` là **Canonical Owner** duy nhất nắm giữ dữ liệu chuẩn cho `media_subject` và `media_asset`. Các service khác (như `query-service`) chỉ lưu bản sao (**Projection/Read Model**) phục vụ đọc/tìm kiếm, không có quyền sửa dữ liệu gốc.

### 🔹 CQRS / CQRS Lite (Command Query Responsibility Segregation)
- **🗣️ Cách đọc (Phát âm)**: *Si-Ki-U-A-Es La-it* (hoặc *Si-Ki-U-A-Es*)
- **Giải thích tiếng Việt**: *Mô hình tách biệt trách nhiệm giữa lệnh Ghi (Command/Mutation) và lệnh Đọc (Query)*.
- **Trong dự án**: Tách thành **CQRS Lite** ở mức Microservice:
  - **Write Side (Catalog Service)**: Xử lý nghiệp vụ Ghi/Sửa canonical data vào `catalog_db`.
  - **Read Side (Query Service)**: Xử lý Đọc/Tìm kiếm siêu tốc từ `query_db`, Elasticsearch và Redis.

### 🔹 Transactional Outbox Pattern
- **🗣️ Cách đọc (Phát âm)**: *Trăn-giắc-xơ-nồ Ao-bốc Pát-tơn*
- **Giải thích tiếng Việt**: *Mô hình gửi Event an toàn bằng bảng đệm Outbox trong cùng DB Transaction*.
- **Trong dự án**: Khi `scan-service` hoặc `catalog-service` thay đổi dữ liệu, nó sẽ ghi entity nghiệp vụ VÀ ghi một bản tin event vào bảng `outbox` trong **cùng 1 Local DB Transaction**. Tiến trình **Outbox Relay** sẽ quét bảng outbox này để push sang Kafka. Đảm bảo dữ liệu ghi DB thành công thì Event chắc chắn không bị mất.

### 🔹 Eventual Consistency
- **🗣️ Cách đọc (Phát âm)**: *I-ven-chu-ồ Cân-sis-tân-si*
- **Giải thích tiếng Việt**: *Tính đồng bộ dữ liệu sau một khoảng thời gian (Bất đồng bộ / Cuối cùng rồi cũng đồng bộ)*.
- **Trong dự án**: Khi vừa Ghi thành công ở `catalog-service`, dữ liệu sẽ mất vài milisecond/giây để truyền qua Kafka và cập nhật lên `query-service`. Chấp nhận khoảng trễ nhỏ này đổi lại hiệu năng và sự độc lập giữa các service.

### 🔹 Hexagonal Architecture (Ports and Adapters)
- **🗣️ Cách đọc (Phát âm)**: *Héc-xa-go-nồ Ác-ki-tếch-chơ*
- **Giải thích tiếng Việt**: *Kiến trúc Lục giác / Tách biệt lõi nghiệp vụ khỏi chi tiết kỹ thuật*.
- **Trong dự án**: Mỗi microservice chia package thành `domain` (lõi), `application` (use-case), `adapter.in` (web/REST/Kafka consumer), `adapter.out` (JPA repository/Kafka producer), `config`.

### 🔹 Idempotency / Idempotent Consumer
- **🗣️ Cách đọc (Phát âm)**: *Ai-đềm-pơ-tân-si* / *Ai-đềm-pơ-tần-t Cơn-su-mơ*
- **Giải thích tiếng Việt**: *Tính trùng lặp không làm thay đổi kết quả (Tính giao hoán)*.
- **Trong dự án**: Kafka đảm bảo giao bản tin *At-Least-Once* (có thể bị gửi lặp). Các Kafka Consumer trong Catalog/Query lưu lại `eventId` đã xử lý; nếu nhận lại Event trùng, hệ thống sẽ bỏ qua an toàn mà không làm nhân đôi dữ liệu.

---

## 🎬 2. Thuật ngữ Nghiệp vụ Media (Domain & Business Terms)

### 🔹 Subject (`media_subject`)
- **🗣️ Cách đọc (Phát âm)**: *Sắp-giếch-t*
- **Giải thích tiếng Việt**: *Thực thể Media chính / Chủ thể gốc (như một Bộ Phim Video hoặc một Album Ảnh)*.

### 🔹 Asset (`media_asset`)
- **🗣️ Cách đọc (Phát âm)**: *Át-xét*
- **Giải thích tiếng Việt**: *File vật lý cụ thể thuộc về một Subject*.
- **Trong dự án**: Một Subject có thể có nhiều Asset phụ thuộc: file video chính (`PRIMARY_VIDEO`), file ảnh đại diện (`IMAGE`), file xem thử ngắn (`GIF`).

### 🔹 Proposal (Scan Proposal)
- **🗣️ Cách đọc (Phát âm)**: *Prơ-pấu-giồ* (Scan Prơ-pấu-giồ)
- **Giải thích tiếng Việt**: *Bản đề xuất nhập liệu nháp*.
- **Trong dự án**: Khi `scan-service` quét thư mục vật lý, nó chưa ghi ngay vào Catalog DB mà lưu dưới dạng Proposal. Người dùng/Admin phải xem xét và nhấn **Approve** thì Proposal mới chuyển thành Event nhập liệu thật.

### 🔹 Identity Key
- **🗣️ Cách đọc (Phát âm)**: *Ai-đen-ti-ti Ki*
- **Giải thích tiếng Việt**: *Khóa định danh tự nhiên của Media*.
- **Trong dự án**: Chuỗi ký tự chuẩn hóa từ mã phim/tên folder (ví dụ: mã code JOKE hoặc tên folder USE Album). Dùng để ghép nối các file liên quan và chống tạo trùng Subject.

### 🔹 Parsing Strategy / Registry
- **🗣️ Cách đọc (Phát âm)**: *Pát-sing Stơ-rát-tơ-gi* / *Rê-gis-trì*
- **Giải thích tiếng Việt**: *Chiến lược & Tập đăng ký phân tích cú pháp tên file*.
- **Trong dự án**: Các class đọc tên file/thư mục để tự nhận diện vùng dữ liệu (Region `JOKE` hay `USE`) và trích xuất ra mã phim chuẩn.

---

## ⚡ 3. Thuật ngữ Xử lý & Truy vấn Dữ liệu (Data & Query Terms)

### 🔹 Projection / Read Model
- **🗣️ Cách đọc (Phát âm)**: *Prơ-giếch-xần* / *Rít Mô-đềnh*
- **Giải thích tiếng Việt**: *Mô hình dữ liệu chuyên biệt phục vụ Đọc/Hiển thị*.
- **Trong dự án**: `query_media_subject` trong `query_db` là bản sao dữ liệu phẳng được dựng sẵn từ Event để Gallery Web query siêu tốc mà không cần JOIN phức tạp.

### 🔹 Hydrate / Hydration
- **🗣️ Cách đọc (Phát âm)**: *Ha-i-đơ-rết* / *Ha-i-đơ-rai-xần*
- **Giải thích tiếng Việt**: *Tiến trình "bơm" / "làm đầy" dữ liệu chi tiết vào một Object nháp*.
- **Trong dự án**: `query-service` gửi từ khóa lên Elasticsearch để lấy về danh sách `subjectId` phù hợp (Fast Hit), sau đó **Hydrate** (lấy đầy đủ thông tin chi tiết) các ID này từ PostgreSQL/Redis để trả về cho Frontend.

### 🔹 Reconcile / Reconciliation
- **🗣️ Cách đọc (Phát âm)**: *Ri-cơn-sai-ồ* / *Ri-cơn-si-li-ai-xần*
- **Giải thích tiếng Việt**: *Tiến trình đối soát và hợp nhất dữ liệu*.
- **Trong dự án**: Khi `query-service` nhận Event snapshot mới, nó so sánh danh sách Assets cũ và mới để tự động **Thêm asset mới / Cập nhật asset cũ / Xóa asset không còn tồn tại** cho khớp 100% với Catalog.

### 🔹 Degraded Response / Graceful Degradation
- **🗣️ Cách đọc (Phát âm)**: *Đi-grây-địt Ris-pons* / *Grấy-xơ-phu Đi-grơ-đây-xần*
- **Giải thích tiếng Việt**: *Phản hồi hạ cấp an toàn khi gặp sự cố*.
- **Trong dự án**: Nếu Elasticsearch bị treo/lỗi, Query Service tự động chuyển sang chế độ **Fallback** tìm kiếm bằng PostgreSQL text search. Hệ thống vẫn chạy được (chỉ chậm hơn chút) chứ không sập API.

### 🔹 Cache-Aside Pattern
- **🗣️ Cách đọc (Phát âm)**: *Kếch Ơ-xai Pát-tơn* (Cache đọc là *Kếch*)
- **Giải thích tiếng Việt**: *Mô hình đọc/ghi Cache bên cạnh Database*.
- **Trong dự án**: Query Service kiểm tra Redis trước → nếu có (Hit) thì trả về ngay → nếu không (Miss) thì đọc PostgreSQL rồi ghi lại vào Redis. Khi có Event thay đổi, Cache sẽ bị xóa (**Evict** - đọc là *I-vếch-t*).

---

## 🔁 4. Thuật ngữ Kafka, Outbox & Reliability

### 🔹 DLT (Dead Letter Topic) / DLE (Dead Letter Event)
- **🗣️ Cách đọc (Phát âm)**: *Đét Lét-tơ Tóp-pic* / *Đét Lét-tơ I-ven-t*
- **Giải thích tiếng Việt**: *Hàng đợi / Bảng chứa các bản tin lỗi không thể phục hồi*.
- **Trong dự án**: Khi một Kafka Consumer gặp lỗi và đã Retry quá số lần quy định, Event đó sẽ được chuyển vào DLT (`*.DLT`) và bảng `dead_letter_event` để khoanh vùng và chờ người dùng xử lý thủ công.

### 🔹 Outbox Relay / Publisher
- **🗣️ Cách đọc (Phát âm)**: *Ao-bốc Rơ-lay* / *Pắp-linh-xơ*
- **Giải thích tiếng Việt**: *Tiến trình quét đệm Outbox và phát Event ra Kafka*.
- **Trong dự án**: Worker chạy ngầm theo chu kỳ, quét các dòng event chưa gửi trong bảng `outbox` và đẩy sang Kafka Broker.

### 🔹 At-Least-Once Delivery
- **🗣️ Cách đọc (Phát âm)**: *Ắt Lít Wăn Đi-li-vơ-ri*
- **Giải thích tiếng Việt**: *Cơ chế cam kết giao bản tin ít nhất một lần của Kafka*.
- **Trong dự án**: Đảm bảo bản tin không bị thất lạc, nhưng chấp nhận rủi ro trùng bản tin (bắt buộc Consumer phải Idempotent).

---

## 📊 5. Thuật ngữ Observability & Monitoring

### 🔹 Observability
- **🗣️ Cách đọc (Phát âm)**: *Ọp-giơ-vơ-bi-li-ti*
- **Giải thích tiếng Việt**: *Khả năng quan sát, đo lường và hiểu được trạng thái bên trong của hệ thống thông qua dữ liệu đầu ra*.

### 🔹 Structured Logging (ECS JSON)
- **🗣️ Cách đọc (Phát âm)**: *Sơ-trắc-chơ Log-ging* (*I-Si-Es Gei-sần*)
- **Giải thích tiếng Việt**: *Ghi log dưới dạng cấu trúc dữ liệu JSON (Chuẩn Elastic Common Schema)*.
- **Trong dự án**: Thay vì in chữ thường, log được xuất dạng JSON có trường rõ ràng (`service.name`, `@timestamp`, `correlationId`, `log.level`), giúp Logstash & Kibana dễ phân tích và tìm kiếm.

### 🔹 Correlation ID / MDC (Mapped Diagnostic Context)
- **🗣️ Cách đọc (Phát âm)**: *Co-rơ-lay-xần Ai-Đi* / *Em-Đi-Si*
- **Giải thích tiếng Việt**: *Mã định danh duy nhất cho một Request/Flow luồng xử lý*.
- **Trong dự án**: Đã được inject từ Gateway qua HTTP Header và lưu trong MDC (ThreadLocal) của Java để nối toàn bộ log từ Scan → Catalog → Query thành một luồng duy nhất.

### 🔹 Scrape (Prometheus Scrape)
- **🗣️ Cách đọc (Phát âm)**: *Sơ-crếp* (Prơ-mi-thi-ớt Sơ-crếp)
- **Giải thích tiếng Việt**: *Hành động kéo/kết nối thu thập dữ liệu định kỳ*.
- **Trong dự án**: Prometheus Server chủ động gửi HTTP GET request 5 giây/lần tới `/actuator/prometheus` của 5 microservices để thu thập metrics.

### 🔹 Opt-in Profile
- **🗣️ Cách đọc (Phát âm)**: *Ọp-tin Prô-fai*
- **Giải thích tiếng Việt**: *Cấu hình bật tùy chọn khi cần*.
- **Trong dự án**: Profile Docker Compose `--profile observability` giúp dev chọn bật stack giám sát khi cần debug, không ép buộc chạy tốn tài nguyên máy local.
