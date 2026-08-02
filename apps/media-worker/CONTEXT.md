# Media Worker context

## Scope

Xử lý nền cho technical media metadata, thumbnail, GIF preview và hash file. Media delivery là trách nhiệm của Nginx theo `ADR-005`.

## Owns

- Kafka consumer group cho processing job.
- Adapter filesystem và công cụ media.
- Event `media.processing.completed.v1` cùng lỗi processing có cấu trúc.
- Root registry và safe-path resolution phục vụ processing job.

## Invariants

- Tra asset locator qua Catalog API; không truy cập database của Catalog/Query.
- Job phải idempotent theo file/version và có retry/DLT.
- Concurrency giới hạn theo I/O thực tế, không tạo virtual thread vô hạn.
- Không ghi Catalog database trực tiếp; chỉ publish event.
- Dùng `platform/observability` cho direct-request correlation MDC; expose Prometheus chỉ trên direct
  service port và không dùng storage key/path làm metric label.
- ECS JSON log không được chứa absolute media path; ELK lỗi không được chặn processing job.
