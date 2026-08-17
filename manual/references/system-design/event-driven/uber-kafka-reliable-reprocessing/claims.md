# Claims: Uber Kafka Reliable Reprocessing & DLQ

- **Head-of-Line Blocking**: Retry đồng bộ và không commit offset trên main Kafka topic sẽ làm tắc nghẽn toàn bộ partition, khiến các event mới và hợp lệ bị delay nghiêm trọng.
- **Non-blocking Offloading**: Đẩy failed message sang Retry Topic và commit ngay offset trên Main Topic giúp duy trì throughput thời gian thực của pipeline chính.
- **Tiered Exponential Backoff Topics**: Dùng nhiều cấp Retry Topic với độ trễ tăng dần (ví dụ: 1m, 5m, 15m) giúp downstream dependency có thời gian hồi phục mà không cần blocking sleep trong consumer thread.
- **Dead Letter Queue (DLQ) Isolation**: DLQ là trạm cuối cùng cô lập các "poison pills" để ngăn chặn việc lãng phí tài nguyên CPU/Network cho các thông điệp vĩnh viễn không xử lý được.
- **Trace Context Propagation**: Metadata về retry attempt, error reason và distributed trace context (OpenTelemetry/W3C) phải được bảo toàn xuyên suốt qua các header của Kafka message.
- **Idempotent Consumers Required**: Khi một event được replay từ retry topic hoặc DLQ, thứ tự ban đầu có thể bị xáo trộn (out-of-order) $\rightarrow$ Bên nhận bắt buộc phải có cơ chế Idempotent Processing (dựa trên entity version hoặc unique event ID).
