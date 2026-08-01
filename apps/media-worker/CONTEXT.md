# Media Worker context

## Scope

Xử lý nền cho technical media metadata, thumbnail, GIF preview và hash file; phát read-only media content cho frontend qua Gateway.

## Owns

- Kafka consumer group cho processing job.
- Adapter filesystem và công cụ media.
- Event `media.processing.completed.v1` cùng lỗi processing có cấu trúc.
- Root registry, safe-path resolution và HTTP GET/HEAD/Range cho media content.

## Invariants

- Content API chỉ public qua Gateway; không nhận path tự do hoặc absolute path từ frontend.
- Tra asset locator qua Catalog API; không truy cập database của Catalog/Query.
- Job phải idempotent theo file/version và có retry/DLT.
- Concurrency giới hạn theo I/O thực tế, không tạo virtual thread vô hạn.
- Không ghi Catalog database trực tiếp; chỉ publish event.
