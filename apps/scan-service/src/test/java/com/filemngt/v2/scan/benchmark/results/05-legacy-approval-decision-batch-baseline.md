# Benchmark 05 — Legacy Approval Decision Batch Baseline (FT-045)

- **Class thực thi**: [`LegacyScanDecisionBatchBenchmarkIT.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/approval/legacy/LegacyScanDecisionBatchBenchmarkIT.java)
- **Implementation đo**: `ScanDecisionService.decideAll(...)` → `ScanRunDecisionBatch.decideAll(...)`
- **Workload mục tiêu**: 1.000.000 proposals, approve-all
- **Môi trường**: Laptop người dùng, Java 25, PostgreSQL Testcontainers `postgres:18.0-alpine`
- **Thời điểm đo**: 2026-08-17

## Kết quả runtime

| Workload | Kết quả | Thời gian | Throughput | Trạng thái |
|---:|---|---:|---:|---|
| 25.000 proposals | Decision + outbox hoàn tất | 4.139 ms | 6.040 records/s | PASS |
| 1.000.000 proposals | Crash trước khi ghi decision/outbox | Không có | Không có | BLOCKED |

Lần chạy 25k cũng có warmup 5.925 ms; số dùng để so sánh là measured run 4.139 ms.

## Failure evidence ở workload 1M

Legacy path load toàn bộ proposal ID rồi gọi:

```java
decisions.findAllById(scanProposals.stream().map(ScanProposalEntity::id).toList())
```

Hibernate tạo một câu `WHERE proposal_id IN (?, ?, ... )` với **1.000.000 bind parameters**. PostgreSQL JDBC giới hạn prepared statement ở **65.535 parameters**, nên phép đo dừng tại bước idempotency lookup:

```text
PSQLException: PreparedStatement can have at most 65,535 parameters.
Given query has 1,000,000 parameters
```

Vì lỗi xảy ra trước `decisions.saveAll()` và `outbox.saveAll()`, không có số liệu throughput end-to-end 1M của legacy path. Đây là failure boundary thực tế, không phải timeout hay OOM.

## Kết luận baseline

`25k` là calibration run thành công, không được nội suy thành evidence 1M. Workload 1M chứng minh legacy implementation không scale đến bước idempotency lookup: nó cần loại bỏ giant `IN (...)` bằng chunked/keyset query hoặc set-based database operation trước khi có thể đo tiếp chi phí tạo và ghi decision/outbox.
