# Summary: Uber Kafka Reliable Reprocessing & DLQ

## Ứng dụng trực tiếp cho Backend V2
Bài viết này là chuẩn mực kiến trúc cho hệ thống **Event Outbox và Consumer Processing** của dự án `file-mngt-be-v2`:
1. **Outbox Event Publisher & Kafka Consumer:**
   * Không bao giờ dùng vòng lặp retry đồng bộ trong Kafka Listener của `catalog-service` hay `query-service` khi gặp lỗi downstream (ví dụ lỗi DB lock, remote service 503).
   * Thay vào đó, áp dụng pattern **Non-blocking Tiered Retry Topics** (`<topic>-retry-1`, `<topic>-retry-2`) và cuối cùng là `<topic>-dlt` (Dead Letter Topic).
2. **Bảo toàn Trace Context:**
   * Đúng với thiết kế `KafkaTracingHeaderPropagation` và `ScanOutboxEventEntity` trong dự án: luôn truyền `correlation_id`, `traceparent` và `attempt_count` trong message header khi chuyển tiếp giữa các retry topics.
3. **Quản trị DLQ (Dead Letter Queue):**
   * Các outbox event hoặc consumer event rơi vào DLQ phải có bảng quản trị trạng thái (như `unresolved_dlt_count` trong `scan_approval_operation`) để hỗ trợ chẩn đoán và replay sau khi fix bug.
