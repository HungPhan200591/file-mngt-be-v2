# FT020 — OpenTelemetry Tracing & Kafka Header Propagation — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: platform/observability
- Scope/files:
  - `platform/observability/src/main/java/com/filemngt/v2/observability/kafka/KafkaTracingHeaderPropagation.java`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/messaging/KafkaOutboxMessagePublisher.java`
  - `apps/catalog-service/src/main/java/com/filemngt/v2/catalog/adapter/out/messaging/KafkaCatalogOutboxMessagePublisher.java`
  - `apps/scan-service/src/main/resources/db/migration/V5__add_outbox_tracing_context.sql`
  - `apps/catalog-service/src/main/resources/db/migration/V6__add_outbox_tracing_context.sql`
  - `apps/catalog-service/src/main/java/com/filemngt/v2/catalog/adapter/in/event/MediaFileDiscoveredConsumer.java`
  - `apps/query-service/src/main/java/com/filemngt/v2/query/adapter/in/event/MediaSubjectChangedConsumer.java`
- Must preserve:
  - Tương thích 100% với `CorrelationIdMdcFilter` và header `X-Correlation-Id` hiện tại.
  - Tương thích 100% với schema JSON payload của tất cả Event V1/V2.
  - Không phá vỡ luồng retry / Dead Letter Topic đã cấu hình ở FT005/FT006.
- Read on demand:
  - [CorrelationId.java](../../../platform/observability/src/main/java/com/filemngt/v2/observability/CorrelationId.java)
  - [03-correlation-id-tracing.md](../../../manual/learning/deep-dive/observability/03-correlation-id-tracing.md)

## Bước triển khai

1. **Bổ sung Kafka Tracing Header Helper (`platform/observability`)**:
   - Xây dựng `KafkaTracingHeaderPropagation` chứa 2 hàm tiện ích:
     - `injectTracingHeaders(ProducerRecord<?, ?> record)`: Đọc MDC (`correlationId`) và inject header `X-Correlation-Id` & `traceparent`.
     - `extractAndSetMdc(ConsumerRecord<?, ?> record)`: Đọc header từ Kafka Record, gán MDC `correlationId` và trả về `AutoCloseable` để dọn dẹp MDC trong khối `try-with-resources`.

2. **Cập nhật Outbox Publishers (`scan-service`, `catalog-service`)**:
   - Persist `correlationId` và `traceparent` ngoài payload trong cùng transaction outbox; relay khôi phục context rồi gọi `injectTracingHeaders` trước `kafka.send(...)`.

3. **Cập nhật Kafka Consumers (`catalog-service`, `query-service`)**:
   - Cập nhật `MediaFileDiscoveredConsumer`, `MediaSubjectChangedConsumer` và `CatalogDeadLetterObserver` bọc khối xử lý tiêu thụ trong `try (var ignored = KafkaTracingHeaderPropagation.extractAndSetMdc(record)) { ... }`.

4. **Viết Unit & Integration Test**:
   - Thêm unit test trong `platform/observability` đảm bảo inject/extract header hoạt động chính xác và MDC được dọn sạch 100%.

## Kiểm tra

- Run unit tests: `./mvnw test -pl platform/observability`
- Kiểm tra E2E HTTP & Kafka log tracing: Chạy e2e tests và kiểm tra log output chứa cùng một `correlationId` từ HTTP Controller sang Kafka Consumer.

## Rollout và rollback

- **Rollout**: Triển khai module `platform/observability` trước, sau đó cập nhật dần các service theo thứ tự `scan-service` → `catalog-service` → `query-service`.
- **Rollback**: Nếu có sự cố, xóa bỏ bước inject/extract header mà không ảnh hưởng tới dữ liệu DB hay payload Kafka.

## Tài liệu cần cập nhật

- `docs/STATUS.md`: Cập nhật trạng thái FT020.
- `manual/learning/deep-dive/observability/03-correlation-id-tracing.md`: Cập nhật trạng thái triển khai Kafka Header Tracing từ "Định hướng" thành "Đã triển khai tại FT020".
