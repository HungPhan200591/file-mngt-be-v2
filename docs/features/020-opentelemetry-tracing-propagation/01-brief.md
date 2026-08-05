# FT020 — OpenTelemetry Tracing & Kafka Header Propagation

Owner: platform/observability

## Vấn đề

Hiện tại `CorrelationIdMdcFilter` chỉ hoạt động tại tầng HTTP (Servlet Filter). Khi các sự kiện nghiệp vụ được phát hành ra Kafka thông qua Outbox Relay (`scan-service`, `catalog-service`), context tracing (`correlationId` / `traceId`) bị đứt đoạn tại giao tiếp bất đồng bộ qua Kafka. Do đó:
1. Log tại các Kafka Consumer (`catalog-service`, `query-service`) thiếu `correlationId` liên kết với HTTP Request ban đầu.
2. Chưa áp dụng chuẩn W3C Trace Context (`traceparent` header) để kết nối distributed tracing xuyên suốt hệ thống microservice.

## Mục tiêu và acceptance criteria

1. **Kafka Header Tracing Injection (Outbox Publisher)**:
   - Các Outbox Publisher (`KafkaOutboxMessagePublisher` ở `scan-service` và `KafkaCatalogOutboxMessagePublisher` ở `catalog-service`) lưu durable `correlationId` và `traceparent` ngoài JSON payload của outbox, rồi khôi phục context để inject vào Kafka `RecordHeaders` dưới các khóa `X-Correlation-Id` và `traceparent`.
2. **Kafka Header Tracing Extraction & MDC Bridge (Consumer)**:
   - Các Kafka Consumer (`MediaFileDiscoveredConsumer`, `MediaSubjectChangedConsumer`) đọc Kafka `RecordHeaders` khi tiêu thụ message, gán `correlationId` và, khi có `traceparent` hợp lệ, `trace_id` vào SLF4J MDC của thread tiêu thụ.
   - Bắt buộc thực hiện `MDC.remove(...)` hoặc `MDC.clear()` sau khi hoàn tất xử lý event để tránh rò rỉ context giữa các lần polling/consume.
3. **OpenTelemetry Context Bridge**:
   - Module `platform/observability` cung cấp helper / Interceptor chuẩn hóa cơ chế propagation W3C Trace Context và tương thích ngược với header `X-Correlation-Id` hiện tại.
4. **Tương thích Contract & Không phá vỡ Payload**:
   - Truyền context qua Kafka Record Header, giữ nguyên 100% schema payload của các Event V1/V2 (`MediaFileDiscoveredV1`, `MediaSubjectChangedV1`, ...).

## Ngoài phạm vi

- Không thay đổi schema JSON payload của các Kafka Event đã công bố.
- Chưa bật remote exporter (Jaeger / Grafana Tempo OTLP endpoint) trong phạm vi task này; chỉ tập trung vào Header Propagation & MDC Bridging cục bộ.
- Không thay đổi hành vi ghi log của Logback ECS JSON formatter đã dựng ở FT014.

## Câu hỏi/rủi ro mở

- **Overhead hiệu năng**: Cần đảm bảo việc serialize/deserialize Header String trên Kafka Record diễn ra nhẹ nhàng, không gây ảnh hưởng throughput của Outbox Relay.
- **Fallback khi thiếu Header**: Khi nhận event cũ hoặc event từ nguồn bên ngoài thiếu `X-Correlation-Id`, Consumer tự sinh `correlationId` để ghi log thay vì gây lỗi NPE hay crash consumer. Nếu thiếu hoặc sai `traceparent`, không đặt `trace_id` vào MDC.
