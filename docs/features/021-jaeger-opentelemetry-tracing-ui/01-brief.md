# FT021: Tích hợp Jaeger UI & OpenTelemetry OTLP Exporter

Owner: `platform/observability`

## Vấn đề

Feature FT020 đã hoàn thành việc truyền Trace Context (`traceparent` W3C) và MDC Logging Correlation. Tuy nhiên, hệ thống chưa có giao diện Gantt Chart trực quan (như Jaeger UI hay Grafana Tempo) để kỹ sư xem sơ đồ độ trễ và luồng gọi distributed trace giữa các microservice bằng 1-Click mà không phải tra cứu hai lần trên Kibana.

## Mục tiêu và acceptance criteria

1. **Bổ sung Container `jaeger` vào `infra/compose/compose.yaml`**:
   - Sử dụng image `jaegertracing/all-in-one:1.60.0`.
   - Gắn profile `observability`.
   - Expose Jaeger Web UI tại host port `18122` (`http://localhost:18122`).
   - Expose OTLP gRPC endpoint tại host port `18123` (nội bộ container network: `jaeger:4317`).

2. **Cấu hình OpenTelemetry OTLP Exporter trong Spring Boot**:
   - Dùng `spring-boot-starter-opentelemetry` tại `platform/observability` để Spring Boot tự cấu hình exporter.
   - Cấu hình OTLP HTTP endpoint tại `${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:18124/v1/traces}`.

3. **Cung cấp 1-Click Trace Visualization trên Jaeger UI**:
   - Khi user gọi API Approve scan proposal trên `gateway-service` / `scan-service`, toàn bộ Span của Request HTTP, Outbox Publish, Kafka Transport, Catalog Consumer và Query Consumer được gửi về Jaeger.
   - Tìm theo `trace_id` hoặc tag `correlation.id` trong Jaeger UI (`http://localhost:18122`) hiển thị sơ đồ cây Gantt Chart trọn vẹn.

## Ngoài phạm vi

- Không thay đổi nghiệp vụ REST API hoặc schema DB của các domain service (`scan`, `catalog`, `query`, `media-worker`).
- Không triển khai OpenTelemetry Collector phức tạp cho môi trường Production (môi trường local dùng Jaeger All-in-One làm OTLP receiver).

## Câu hỏi/rủi ro mở

- Rủi ro: Nếu Jaeger container chưa bật, ứng dụng Spring Boot có bị nghẽn không?
  - Giải pháp: Cấu hình OTLP Exporter dạng non-blocking batch span processor với timeout ngắn để ứng dụng không bị block nếu Jaeger offline.
