# 🔗 Correlation ID & Distributed Tracing Deep-Dive

Tài liệu đi sâu vào cơ chế lan truyền Correlation ID, quản lý SLF4J MDC trong Thread Pool Java, mô hình truy vết liên kết theo Entity Key và định hướng phát triển Distributed Tracing xuyên Kafka với OpenTelemetry.

---

## 1. Cơ chế Lan truyền Correlation ID qua HTTP & MDC

Trong Backend V2, mọi thao tác người dùng đều được đánh dấu bằng một **Correlation ID** (dạng UUID) để theo dõi xuyên suốt các dịch vụ.

```mermaid
sequenceDiagram
    autonumber
    actor User as Client / E2E Test
    participant GW as gateway-service (18100)
    participant SCAN as scan-service (18102)
    participant MDC as SLF4J MDC (ThreadLocal)

    User->>GW: HTTP Request (Optional: X-Correlation-Id)
    Note over GW: If header missing,<br/>generate new UUID
    GW->>SCAN: HTTP Request + Header X-Correlation-Id: <UUID>
    SCAN->>MDC: CorrelationIdMdcFilter: MDC.put("correlationId", UUID)
    SCAN->>SCAN: Execute Use Case Logic (Log automatically includes correlationId)
    SCAN->>MDC: finally { MDC.clear() }
    SCAN-->>GW: HTTP Response + Header X-Correlation-Id: <UUID>
    GW-->>User: HTTP Response
```

### 1.1. Chi tiết Implementation `CorrelationIdMdcFilter`
Dự án đóng gói module dùng chung `platform/observability` chứa `CorrelationIdMdcFilter`:
```java
public class CorrelationIdMdcFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        try {
            MDC.put("correlationId", correlationId);
            response.setHeader("X-Correlation-Id", correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.clear(); // BẮT BUỘC: Ngăn rò rỉ Correlation ID sang Request khác trong Thread Pool
        }
    }
}
```

### 1.2. Tại sao BẮT BUỘC phải gọi `MDC.clear()` trong khối `finally`?
- Các Java Web Server (Tomcat / Jetty) sử dụng **Worker Thread Pool** để xử lý request. Khi request hoàn tất, Thread không bị tiêu hủy mà quay trở lại Pool để phục vụ request mới.
- Vì MDC lưu trữ trong `ThreadLocal`, nếu không gọi `MDC.clear()`, Correlation ID của Request cũ sẽ bị rò rỉ (leak) sang Request mới thực thi trên cùng Thread đó, dẫn đến sai lệch dữ liệu tra cứu log.

---

## 2. Mô hình Truy vết Liên kết (Entity Business Key Tracing Model)

Trong mô hình **Event-Driven Architecture (EDA)** bất đồng bộ:
- Mỗi HTTP request trực tiếp có một `correlationId` riêng.
- Để truy vết trọn vẹn toàn bộ vòng đời của dữ liệu từ khi Scan → Approve → Event Outbox → Kafka Consumer → Catalog → Query Projection, chúng ta nối vết bằng **Chuỗi Entity Keys**:

```mermaid
flowchart TB
    SCAN_RUN["<font color='white'>scanRunId<br/>(Scan REST Requests)</font>"] --> EVENT_1["<font color='white'>eventId<br/>(media.file.discovered.v1)</font>"]
    EVENT_1 --> SUBJECT["<font color='white'>catalogSubjectId<br/>(Catalog Subject)</font>"]
    SUBJECT --> EVENT_2["<font color='white'>eventId<br/>(media.subject.changed.v1)</font>"]
    EVENT_2 --> QUERY["<font color='white'>querySubjectId<br/>(Query Projection)</font>"]

    style SCAN_RUN fill:#2196F3,stroke:#fff,stroke-width:2px
    style EVENT_1 fill:#E91E63,stroke:#fff,stroke-width:2px
    style SUBJECT fill:#FF9800,stroke:#fff,stroke-width:2px
    style EVENT_2 fill:#E91E63,stroke:#fff,stroke-width:2px
    style QUERY fill:#4CAF50,stroke:#fff,stroke-width:2px
```

### Các Bước Truy vết theo Command Line (PowerShell):
1. **Tìm tất cả HTTP Request của lượt Scan theo `scanRunId`**:
   ```powershell
   Get-ChildItem -Path logs, apps -Filter "*.log" -Recurse | Select-String "<scanRunId-UUID>"
   ```
2. **Tìm log Scan bắn Event & Catalog tiêu thụ Event theo `eventId`**:
   ```powershell
   Get-ChildItem -Path logs, apps -Filter "*.log" -Recurse | Select-String "<eventId-UUID>"
   ```
3. **Tìm log Catalog bắn Event & Query dựng Projection theo `subjectId` / `identityKey`**:
   ```powershell
   Get-ChildItem -Path logs, apps -Filter "*.log" -Recurse | Select-String "<identityKey-String>"
   ```

---

## 3. Định hướng Tracing Xuyên Kafka (OpenTelemetry Roadmap)

Để tự động hóa việc nối chuỗi vết qua Kafka mà không cần tra cứu thủ công qua ID:
- **Tích hợp OpenTelemetry Java Agent / Micrometer Tracing**.
- **Kafka Record Header Injection**: Tự động inject `traceparent` (W3C Trace Context bao gồm Trace ID và Span ID) vào Kafka Record Headers khi Outbox Relay publish message.
- **Tích hợp Grafana Tempo / Jaeger**: Hiển thị Gantt Chart thời gian xử lý thực tế qua các service (từ REST API → Outbox Relay → Kafka Broker → Consumer Process → FFmpeg Worker).

### 3.1. Mối quan hệ giữa OpenTelemetry và SLF4J MDC (Cầu nối Bridge)

> **Thắc mắc kiến trúc cốt lõi**: *"Khi triển khai OpenTelemetry thì có thể bỏ SLF4J MDC không?"*

**CÂU TRẢ LỜI LÀ KHÔNG**. OpenTelemetry và SLF4J MDC **hoạt động song hành hỗ trợ lẫn nhau**:

1. **MDC là nơi lưu trữ Context theo Thread (ThreadLocal)**:
   - Trong ứng dụng Java, thư viện Logging (Logback/Log4j2) **không thể tự lấy được `trace_id`** nếu không qua MDC.
   - OpenTelemetry Agent khi hoạt động sẽ **tự động bơm (inject/bridge)** `trace_id` và `span_id` vào SLF4J MDC của Thread hiện tại:
     ```java
     // OpenTelemetry Agent tự động thực thi ngầm ở Bytecode level:
     MDC.put("trace_id", Span.current().getSpanContext().getTraceId());
     MDC.put("span_id", Span.current().getSpanContext().getSpanId());
     ```
2. **Cơ chế Nối Vết Log ➔ Trace (Log-to-Trace Correlation)**:
   - Nhờ có `trace_id` trong MDC, mỗi câu log ECS JSON xuất ra file `.json` đều có thêm trường `trace_id`.
   - Giúp Kỹ sư vận hành trên **Kibana Discover** có thể **1-Click từ log lỗi nhảy thẳng sang Grafana Tempo / Jaeger** để xem Gantt Chart toàn bộ cuộc gọi distributed trace đó.
3. **Tiến trình Tiến hóa (Migration Steps)**:
   - **Hiện tại (Backend V2 hiện tại)**: Dùng `CorrelationIdMdcFilter` tự viết để sinh UUID và đẩy vào MDC (`MDC.put("correlationId", uuid)`).
   - **Tương lai (Khi lên OpenTelemetry)**: Có thể gỡ bỏ `CorrelationIdMdcFilter` tự viết vì OTel Agent tự quản lý `traceparent` Header và tự bridge vào MDC, nhưng **vẫn phụ thuộc 100% vào cơ chế MDC của Logback** để in `trace_id` vào file log!

---

## 4. Tài liệu Tham khảo Liên quan
- [00. Tổng quan Observability](00-overview.md)
- [02. Structured Logging: Spring Boot ECS & ELK](02-structured-logging-elk.md)
- [Ngân hàng Câu hỏi Phỏng vấn Tracing & Correlation ID](question-bank/03-tracing-questions.md)
