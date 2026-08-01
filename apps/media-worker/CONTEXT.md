# Media Worker context

## Scope

Xử lý nền cho technical media metadata, thumbnail, GIF preview và hash file.

## Owns

- Kafka consumer group cho processing job.
- Adapter filesystem và công cụ media.
- Event `media.processing.completed.v1` cùng lỗi processing có cấu trúc.

## Invariants

- Không nhận HTTP business request từ frontend.
- Job phải idempotent theo file/version và có retry/DLT.
- Concurrency giới hạn theo I/O thực tế, không tạo virtual thread vô hạn.
- Không ghi Catalog database trực tiếp; chỉ publish event.
