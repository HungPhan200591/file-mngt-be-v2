# FT021: Tích hợp Jaeger UI & OpenTelemetry OTLP Exporter

Owner: `platform/observability`

## Vấn đề

Feature FT020 đã hoàn thành việc truyền Trace Context (`traceparent` W3C) và MDC Logging Correlation. FT021 đã bổ sung Jaeger local cùng OTLP exporter và đã xác nhận runtime một mutation mới tạo trace đầy đủ xuyên HTTP, outbox và Kafka trên Jaeger UI.

## Mục tiêu và acceptance criteria

1. **Bổ sung Container `jaeger` vào `infra/compose/compose.yaml`**:
   - Sử dụng image `jaegertracing/all-in-one:1.60.0`.
   - Gắn các profile Compose `observability` và `tracing`.
   - Expose Jaeger Web UI tại host port `18122` (`http://localhost:18122`).
   - Expose OTLP gRPC tại `18123` và OTLP HTTP tại `18124`; các ứng dụng local dùng OTLP HTTP `http://localhost:18124/v1/traces`.

2. **Cấu hình OpenTelemetry OTLP Exporter trong Spring Boot**:
   - Dùng `spring-boot-starter-opentelemetry` tại `platform/observability` để Spring Boot tự cấu hình exporter.
   - Cấu hình OTLP HTTP endpoint tại `${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:18124/v1/traces}`.

3. **Cung cấp 1-Click Trace Visualization trên Jaeger UI**:
   - Khi user gọi API Approve một scan proposal tạo mutation mới qua Gateway, span của HTTP request, outbox/Kafka producer, Catalog consumer và Query consumer được gửi về Jaeger. Proposal duplicate/no-op có thể không tạo Catalog outbox hay Query consumer span.
   - Tìm theo `trace_id` hoặc tag `correlation.id` trong Jaeger UI (`http://localhost:18122`) hiển thị sơ đồ cây Gantt Chart trọn vẹn.

## Ngoài phạm vi

- Không thay đổi nghiệp vụ REST API hoặc schema DB của các domain service (`scan`, `catalog`, `query`, `media-worker`).
- Không triển khai OpenTelemetry Collector phức tạp cho môi trường Production (môi trường local dùng Jaeger All-in-One làm OTLP receiver).

## Câu hỏi/rủi ro mở

- Rủi ro: Nếu Jaeger container chưa bật, ứng dụng Spring Boot có bị nghẽn không?
  - Ứng dụng vẫn xử lý nghiệp vụ khi Jaeger offline; exporter bất đồng bộ có thể ghi warning khi không kết nối được receiver và không thể xuất batch span.
