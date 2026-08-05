# FT021: Tích hợp Jaeger UI & OpenTelemetry OTLP Exporter — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `platform/observability`
- Scope/files:
  - `infra/compose/compose.yaml`
  - `docs/adr/ADR-004-local-port-allocation.md`
  - `platform/observability/pom.xml`
  - `platform/observability/src/main/java/com/filemngt/v2/observability/autoconfigure/ObservabilityAutoConfiguration.java`
  - `apps/*/src/main/resources/application.yml`
- Must preserve:
  - Microservices REST & Kafka contracts.
  - ECS structured JSON file logging format.
  - Fail-safe behavior (app không sập khi Jaeger offline).
- Read on demand:
  - `docs/features/021-jaeger-opentelemetry-tracing-ui/01-brief.md`
  - `docs/features/021-jaeger-opentelemetry-tracing-ui/02-design.md`

## Bước triển khai

1. **Cập nhật Docker Compose (`infra/compose/compose.yaml`)**:
   - Thêm service `jaeger` (image `jaegertracing/all-in-one:1.60.0`) dưới hai profile `observability` và `tracing`.
   - Expose ports `18122:16686` (UI), `18123:4317` (OTLP gRPC) và `18124:4318` (OTLP HTTP).

2. **Thêm OpenTelemetry OTLP Exporter dependency & Config**:
   - Dùng `spring-boot-starter-opentelemetry` tại `platform/observability`; Spring Boot tự cấu hình OTLP exporter thay vì tự tạo exporter bean không được tracer provider dùng.
   - Dùng `management.opentelemetry.tracing.export.otlp.endpoint` trỏ tới `http://localhost:18124/v1/traces` (OTLP HTTP).

3. **Cấu hình Spring Boot Application YAML**:
   - Thêm cấu hình OTLP tracing endpoint vào `application.yml` của các dịch vụ `gateway-service`, `scan-service`, `catalog-service`, `query-service`, `media-worker`; tắt OTLP metrics export (Prometheus vẫn là metrics exporter) và bật Spring Kafka Observation tại producer/consumer phù hợp.

4. **Kiểm chứng & Chạy thử**:
   - Biên dịch dự án bằng JDK 25 (`.\mvnw test-compile`).
   - Chạy unit/integration test cho helper và outbox metadata.
   - Chờ kiểm chứng runtime Jaeger theo acceptance criteria trước khi chuyển `DONE`.

## Kiểm tra

- Command: `powershell -Command "$env:JAVA_HOME='C:\Users\admin\.jdks\corretto-25.0.4'; .\mvnw test -pl platform/observability -am"`
- Manual test: Khởi động Jaeger UI tại `http://localhost:18122`, thực hiện 1 request Approve tạo mutation mới và xác nhận trace có các span HTTP, outbox/Kafka producer, Catalog consumer và Query consumer. Không dùng `X-Correlation-Id` UUID làm Jaeger Trace ID; tìm trace theo service hoặc tag `correlation.id`.

## Rollout và rollback

- Rollout: Commit thay đổi trong `platform/observability` và `compose.yaml`.
- Rollback: Tắt profile `observability` trong Docker Compose hoặc khôi phục commit cũ.

## Tài liệu cần cập nhật

- `docs/STATUS.md`: Giữ `021-jaeger-opentelemetry-tracing-ui` ở `READY` đến khi có bằng chứng runtime Jaeger.
- `manual/learning/deep-dive/observability/05-opentelemetry-overview.md`: Cập nhật khi runtime acceptance Jaeger hoàn tất.
