# 029 Async non-blocking logging foundation — Plan

Status: `IMPLEMENTED — pending runtime verification`
Brief: [01-brief.md](./01-brief.md)  
Design: [02-design.md](./02-design.md)

## Execution capsule

- **Owner**: `cross-service` (`catalog-service`, `scan-service`, `query-service`, `gateway-service`, `media-worker`)
- **Scope**:
  - Thêm `logback-spring.xml` chuẩn Async Non-blocking cho cả 5 microservices.
  - Refactor cleanup log rác / hot-loop logging trong `ScanParallelAnalyzer`, `ScanChunkCommitter`, `ScanExecutor`...
  - Thêm MDC `runId` ở scan worker; chưa thêm AOP vì chưa có use case timing dùng chung được chốt.
- **Must preserve**:
  - Toàn bộ REST API contracts, Kafka events, DB transactions, và business behavior.
- **Read on demand**:
  - `docs/architecture/03-CODING_RULES.md`

## Các bước triển khai

1. **Tạo Cấu hình Logback Async**:
   - Thêm `logback-spring.xml` ở `apps/scan-service/src/main/resources/logback-spring.xml`.
   - Thêm `logback-spring.xml` ở `apps/catalog-service/src/main/resources/logback-spring.xml`.
   - Thêm `logback-spring.xml` ở `apps/query-service/src/main/resources/logback-spring.xml`.
   - Thêm `logback-spring.xml` ở `apps/gateway-service/src/main/resources/logback-spring.xml`.
   - Thêm `logback-spring.xml` ở `apps/media-worker/src/main/resources/logback-spring.xml`.

2. **MDC Helpers**:
   - Đặt `runId` tại scan worker boundary bằng `MDC.putCloseable(...)`.
   - Hoãn `@LogExecution` Aspect đến khi có use case timing chung; không dùng Aspect để thay thế metric/trace.

3. **Cleanup & Refactor Logging trong Hot-Paths**:
   - Refactor `ScanParallelAnalyzer`: Gỡ bỏ log spam ở từng partition execution, chỉ giữ summary log ở đầu/cuối parallel analyze.
   - Refactor `ScanChunkCommitter`: Rút gọn log commit, dùng MDC context thay cho manual string concatenation.
   - Refactor `ScanExecutor`: Tối ưu log hygiene.

4. **Verify & Audit**:
   - Đảm bảo tất cả Java file dưới 500 dòng và tuân thủ `03-CODING_RULES.md`.
