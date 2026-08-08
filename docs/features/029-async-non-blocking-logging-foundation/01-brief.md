# 029 Async non-blocking logging foundation

Owner: `cross-service` (`catalog-service`, `scan-service`, `query-service`, `gateway-service`, `media-worker`)

## Vấn đề

1. **Synchronous Blocking I/O**: Mặc định Spring Boot Console Appender thực thi Synchronous Blocking I/O trực tiếp trên application thread (kể cả Virtual Threads). Khi log nhiều hoặc chạy parallel batch (ví dụ: scan 1M files), I/O stdout/disk gây lock/contention làm giảm throughput nghiêm trọng.
2. **Nhiễu Business Logic (Cognitive Load)**: Các câu lệnh `LOGGER.info(...)` và `LOGGER.debug(...)` bị chèn trực tiếp trong core business logic (use cases, domain parsers, parallel analyzers), làm đứt gãy luồng đọc code, kéo dài method và vi phạm Single Responsibility Principle (SRP).
3. **Log Spam trong Hot Loops**: Log rải rác từng item trong vòng lặp làm tăng allocation rác GC và gây nghẽn I/O không cần thiết.
4. **Thiếu Structured MDC Tracking**: Tự ghép chuỗi `runId`, `traceId` thủ công vào log message thay vì dùng MDC (Mapped Diagnostic Context) và OpenTelemetry correlation ID chuẩn hóa.

## Mục tiêu và acceptance criteria

- Chuyển I/O console và JSON file sang `ch.qos.logback.classic.AsyncAppender`, `queueSize=16384`, `discardingThreshold=0`. Khi queue đầy, policy hiện tại ưu tiên không mất log (`neverBlock=false`) và chấp nhận backpressure ngắn thay vì drop im lặng.
- Định dạng JSON Structured Logging chuẩn hóa chứa `timestamp`, `level`, `service`, `traceId`, `runId`, `logger`, `message`, `exception`.
- Tách biệt logging khỏi core business logic trong các hot path (`ScanParallelAnalyzer`, `ScanChunkCommitter`, `ScanExecutor`...):
  - Gỡ bỏ log spam trong hot loops.
  - Tự động truyền `runId`/`traceId` qua MDC context.
  - Dùng MDC tại async boundary; AOP `@LogExecution` chỉ triển khai khi có use case timing chung đã được chốt.
- Không thay đổi REST contracts, Kafka contracts, hay business behavior của bất kỳ microservice nào.
- Code Java tuân thủ `docs/architecture/03-CODING_RULES.md` (method < 30 lines, class < 250 lines).

## Ngoài phạm vi

- Không thay đổi log storage backend (Elasticsearch/Loki/CloudWatch infrastructure setup).
- Không thay đổi Jaeger OpenTelemetry tracing propagation logic (FT020/FT021).
