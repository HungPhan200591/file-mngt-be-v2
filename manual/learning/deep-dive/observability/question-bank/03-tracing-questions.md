# ❓ Correlation ID & Tracing — Interview Question Bank

Bộ câu hỏi phỏng vấn Chuyên sâu (Senior / Lead) về Correlation ID, SLF4J MDC, Thread Pool Isolation, Entity Business Key Tracing và OpenTelemetry Distributed Tracing.

---

## 📊 Bảng Ma trận Coverage

| Level | Foundation | Senior | Architect | Tổng số câu |
| :--- | :---: | :---: | :---: | :---: |
| **Số lượng** | 1 | 1 | 1 | 3 |

---

## 🎯 Danh sách Câu hỏi Chi tiết & Đáp án Chuẩn

### OBS-TRACE-001 — `SENIOR`
**Question:** Cơ chế nào giúp Correlation ID lan truyền (Propagate) xuyên suốt qua các Thread trong Java Spring Boot mà không làm rò rỉ (leak) ID giữa các Request khác nhau?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Hiểu biết về SLF4J MDC, ThreadLocal trong Java Concurrency và cơ chế hoạt động của Web Servlet Filters.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Dùng **SLF4J MDC (ThreadLocal)** để đính kèm `correlationId` vào Thread xử lý HTTP Request hiện tại, và **BẮT BUỘC gọi `MDC.clear()` trong khối `finally` của Filter** để chống rò rỉ ID khi Tomcat tái sử dụng Thread Pool."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Correlation ID được lưu ở đâu trong Java Thread?** ➔ 💡 **SLF4J MDC (bản chất là ThreadLocal)**.
- ❓ **Làm sao để Correlation ID không bị rò rỉ (leak) giữa 2 Request?** ➔ 💡 **Gọi `MDC.clear()` trong khối `finally` của Filter**.
- ❓ **Điều gì xảy ra nếu quên `MDC.clear()`?** ➔ 💡 **Request B tái sử dụng Worker Thread cũ sẽ bị mang nhầm Correlation ID của Request A**.
- 🔑 **Keyword cốt lõi cần nhớ**: **SLF4J MDC (ThreadLocal) ➔ Filter Inject ➔ try-finally MDC.clear() = No Leak**.

**Answer outline:**
- **Sử dụng SLF4J MDC (Mapped Diagnostic Context)**: MDC sử dụng `ThreadLocal` bên dưới nền để gắn các giá trị key-value vào Thread đang xử lý request.
- **Luồng xử lý tại `CorrelationIdMdcFilter`**:
  1. Filter nhận HTTP Request, trích xuất header `X-Correlation-Id` (hoặc tự sinh UUID mới nếu thiếu).
  2. Đưa vào MDC: `MDC.put("correlationId", correlationId)`.
  3. Mọi câu log được gọi từ Thread này sẽ tự động lấy `correlationId` gắn vào cấu hình ECS JSON log.
- **Phòng chống Rò rỉ (Thread Pool Leak Prevention)**:
  - Tomcat / Jetty tái sử dụng Worker Threads từ Thread Pool. Khi Request A kết thúc, Thread không bị hủy.
  - BẮT BUỘC phải gọi `MDC.clear()` trong khối `finally` của Filter:
    ```java
    try {
        chain.doFilter(request, response);
    } finally {
        MDC.clear(); // Xóa sạch ThreadLocal state
    }
    ```
  - Nếu không `clear()`, Request B đến sau thực thi trên cùng Worker Thread đó sẽ bị **mang nhầm Correlation ID của Request A** ➔ Sai lệch hoàn toàn dữ liệu tra cứu.<br>
**Required trade-offs:** Khi chuyển giao công việc sang ThreadPoolExecutor bất đồng bộ (CompletableFuture, Async task), `ThreadLocal` không tự động truyền sang Thread mới trừ khi dùng `TaskDecorator` hoặc `MDCContextTaskDecorator`.<br>
**Follow-up ladder:** Virtual Threads (Java 21/25) ảnh hưởng thế nào tới `ThreadLocal` và MDC?<br>
**Red flags:** Không sử dụng khối `finally` để `MDC.clear()`.

---

### OBS-TRACE-002 — `SENIOR`
**Question:** Tại sao trong kiến trúc Event-Driven Microservices (EDA), việc tra cứu theo HTTP Correlation ID đơn lẻ chỉ hiển thị 1 log duy nhất, và làm thế nào để truy vết trọn vẹn quy trình xuyên service (Scan → Catalog → Query)?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Khả năng phân tích sự khác biệt giữa Synchronous HTTP Correlation ID và Asynchronous Business Entity Correlation trong EDA.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"HTTP Correlation ID chỉ có hiệu lực trong **duy nhất 1 vòng đời HTTP request**. Để truy vết bất đồng bộ xuyên qua Kafka Broker, phải kết hợp **Chuỗi Entity Keys nghiệp vụ**: `scanRunId` (đợt scan) ➔ `eventId` (Kafka outbox event) ➔ `identityKey` / `subjectId` (tài sản media)."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Tại sao HTTP Correlation ID không tự nối được vết qua Kafka Consumer?** ➔ 💡 **Vì HTTP Client tạo ID ngắn hạn chỉ dùng cho 1 request HTTP đó**.
- ❓ **Giải pháp truy vết bất đồng bộ trong Event-Driven Architecture?** ➔ 💡 **Chuỗi Entity Business Keys (`scanRunId` ➔ `eventId` ➔ `identityKey`)**.
- ❓ **Lợi ích của việc đưa `eventId` vào log outbox & consumer?** ➔ 💡 **Grep log 1 click ra ngay cặp đôi: Service phát hành Event & Service tiêu thụ Event**.
- 🔑 **Keyword cốt lõi cần nhớ**: **HTTP Correlation ID (Single Request) ➔ Business Entity Key Chain (`scanRunId` ➔ `eventId` ➔ `identityKey`)**.

**Answer outline:**
- **Nguyên nhân chỉ có 1 log với HTTP Correlation ID**:
  - Khi client/test harness gọi trực tiếp từng endpoint (ví dụ `POST /api/v2/scans/.../decision`), HTTP client (`httpyac` / Postman) tạo một `X-Correlation-Id` độc lập cho **duy nhất request HTTP đó**.
  - Request hoàn tất ➔ Log HTTP kết thúc. Các bước xử lý bất đồng bộ sau đó (Outbox Relay, Kafka Consumer) không tự mang HTTP Header ID cũ nếu chưa được propagate.
- **Giải pháp: Mô hình Truy vết theo Chuỗi Entity Keys**:
  - Hệ thống sử dụng chuỗi ID liên kết nghiệp vụ để nối vết:
    1. **`scanRunId`**: Lấy danh sách tất cả HTTP requests trong đợt Scan.
    2. **`eventId`**: Tìm log `scan-service` xuất outbox event `media.file.discovered.v1` VÀ log `catalog-service` nhận event tương ứng.
    3. **`identityKey` / `subjectId`**: Tìm log `catalog-service` xuất event `media.subject.changed.v1` VÀ log `query-service` dựng projection.<br>
**Required trade-offs:** Cần log chuẩn hóa chứa các trường `eventId`, `identityKey`, `sourceRelativePath` ở các bước xử lý chính.<br>
**Follow-up ladder:** Làm thế nào để tự động hóa việc nối chuỗi ID này mà không cần grep log bằng tay?<br>
**Red flags:** Cho rằng event-driven async flow có thể dùng chung 1 HTTP correlation ID mà không cần truyền qua Message Headers.

---

### OBS-TRACE-003 — `ARCHITECT`
**Question:** Thiết kế giải pháp Distributed Tracing xuyên Kafka Broker sử dụng chuẩn OpenTelemetry W3C Trace Context (`traceparent`) cho Backend V2 như thế nào?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Kiến thức nâng cao về OpenTelemetry standard, Kafka Record Headers injection/extraction và Grafana Tempo / Jaeger visualization.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Tự động hóa Tracing bằng chuẩn **W3C Trace Context (`traceparent`)**: Outbox Publisher **inject** `traceparent` vào **Kafka Record Headers**, Consumer Interceptor **extract** `traceparent` để tạo Child Span, và đẩy telemetry data về **Grafana Tempo / Jaeger** vẽ Gantt Chart."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Định dạng chuẩn W3C Trace Context là gì?** ➔ 💡 **`traceparent: 00-trace_id-parent_span_id-flags`**.
- ❓ **Truyền Trace Context qua Kafka Broker bằng cách nào?** ➔ 💡 **Inject `traceparent` vào Kafka Record Headers**.
- ❓ **Consumer phía sau nhận Trace Context kiểu gì?** ➔ 💡 **Extract Header `traceparent` để tạo Child Span nối tiếp**.
- ❓ **Công cụ nào dùng để xem Gantt Chart độ trễ?** ➔ 💡 **Grafana Tempo hoặc Jaeger UI**.
- 🔑 **Keyword cốt lõi cần nhớ**: **W3C `traceparent` ➔ Kafka Record Headers Injection ➔ Consumer Extraction ➔ Tempo Gantt Chart**.

**Answer outline:**
1. **Chuẩn W3C Trace Context Specification**:
   - Sử dụng định dạng `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01` (`version-trace_id-parent_span_id-trace_flags`).
2. **Cơ chế Outbox Relay Header Injection**:
   - Khi `ScanOutboxPublisher` đọc event từ DB outbox table, trước khi gửi tin sang Kafka Topic, publisher inject `traceparent` từ context hiện tại vào **Kafka Record Headers**:
     ```java
     record.headers().add("traceparent", currentTraceParent.getBytes(StandardCharsets.UTF_8));
     ```
3. **Cơ chế Kafka Consumer Header Extraction**:
   - Khi `CatalogFileDiscoveryService` tiêu thụ Kafka Record, Consumer Interceptor đọc header `traceparent` và khởi tạo Child Span mới nối tiếp với Parent Trace ID từ Scan Service.
4. **Hiển thị trên Grafana Tempo / Jaeger**:
   - Vẽ Gantt Chart chính xác thời gian thực thi của từng công đoạn: REST Approve (10ms) ➔ Outbox Table DB Save (2ms) ➔ Outbox Relay Publish (15ms) ➔ Kafka Transit (5ms) ➔ Catalog Process (20ms) ➔ Query Projection (12ms).<br>
**Required trade-offs:** Tích hợp OpenTelemetry làm tăng khoảng 1 - 3% CPU overhead và khoảng 100 bytes dung lượng cho mỗi Kafka message.<br>
**Follow-up ladder:** Khác biệt giữa Head-based Sampling và Tail-based Sampling trong Distributed Tracing là gì?<br>
**Red flags:** Cho rằng Kafka Record Header không thể lưu trữ metadata tracing.

---

### OBS-TRACE-004 — `SENIOR`
**Question:** Khi triển khai OpenTelemetry (OTel), chúng ta có thể bỏ hẳn SLF4J MDC và Correlation ID tự viết được không? Mối quan hệ giữa OTel và MDC là gì?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `ARCHITECTURE_EVOLUTION`<br>
**Interviewer evaluates:** Hiểu biết sâu sắc về mối quan hệ giữa Tracing Context Propagation (OTel) và Logging Context (SLF4J MDC).<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"KHÔNG THỂ BỎ MDC. OpenTelemetry và SLF4J MDC hoạt động song hành: OTel quản lý Trace/Span Context xuyên mạng, rồi tự động **bridge (bơm)** `trace_id` vào SLF4J MDC để Logback in ra file log `.json`, tạo cơ chế **1-Click từ Kibana Log sang Jaeger Trace**."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **OpenTelemetry có thay thế SLF4J MDC không?** ➔ 💡 **Không! OTel bridge (bơm) `trace_id` vào MDC**.
- ❓ **Tác dụng của việc OTel bơm `trace_id` vào MDC?** ➔ 💡 **Log-to-Trace Correlation** (Mỗi dòng log JSON tự động có `trace_id`).
- ❓ **Lợi ích lớn nhất khi kết hợp OTel + Kibana Log?** ➔ 💡 **1-Click từ Kibana Log nhảy thẳng sang Jaeger / Tempo Gantt Chart**.
- ❓ **Điểm khác biệt khi tiến hóa từ CorrelationId tự viết lên OTel?** ➔ 💡 **Gỡ `CorrelationIdMdcFilter` tự viết vì OTel Agent tự inject W3C `traceparent` vào MDC**.
- 🔑 **Keyword cốt lõi cần nhớ**: **OTel Trace Context ➔ Bridge vào SLF4J MDC ➔ In ra Log JSON ➔ Log-to-Trace 1-Click**.

**Answer outline:**
- **Không thể bỏ MDC vì**: Thư viện logging (Logback/Log4j2) trong Java chỉ có thể lấy dữ liệu theo ThreadLocal từ MDC để format ra log file.
- **Cơ chế OTel-to-MDC Bridge**:
  - OpenTelemetry Agent khi chạy ngầm sẽ tự động hook vào Bytecode và nạp `trace_id` / `span_id` vào MDC:
    `MDC.put("trace_id", Span.current().getSpanContext().getTraceId());`
- **Tiến trình tiến hóa (Migration Roadmap)**:
  - Phase 1 (Backend V2 hiện tại): Dùng `CorrelationIdMdcFilter` tự viết để gán UUID ngẫu nhiên vào MDC.
  - Phase 2 (Khi bật OTel Agent): Gỡ bỏ Filter tự viết, OTel Agent tự quản lý `traceparent` W3C Header và tự động bridge `trace_id` vào MDC. Logback vẫn dùng MDC để in `trace_id` ra file log.<br>
**Required trade-offs:** Cần cấu hình pattern `%X{trace_id}` hoặc dùng Spring Boot ECS Log formatter để tự động nhặt trường `trace_id` từ MDC.<br>
**Follow-up ladder:** Trong môi trường Async ThreadPool hoặc Reactive WebFlux, OpenTelemetry Agent hỗ trợ truyền Context qua Thread như thế nào?<br>
**Red flags:** Trả lời "Có OpenTelemetry rồi thì xóa sạch SLF4J MDC và Logback".
