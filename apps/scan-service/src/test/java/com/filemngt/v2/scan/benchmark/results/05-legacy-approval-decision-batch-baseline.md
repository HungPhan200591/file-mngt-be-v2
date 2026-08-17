# Benchmark 05 — Approval Decision Batch: Legacy vs FT-045 Candidate

- **Legacy source**: benchmark đã chạy trên checkout trước FT-045; source legacy không còn trong checkout sau khi unstash candidate.
- **Candidate đối chứng**: [`ApprovalDecisionChunkingBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/approval/ApprovalDecisionChunkingBenchmarkTest.java)
- **Legacy implementation**: `ScanDecisionService.decideAll(...)` → `ScanRunDecisionBatch.decideAll(...)`
- **Candidate implementation**: `ApprovalOperationService.accept(...)` → claim → bounded chunk processing
- **Workload mục tiêu**: 1.000.000 proposals, approve-all
- **Môi trường**: Laptop người dùng, Java 25, PostgreSQL Testcontainers `postgres:18.0-alpine`
- **Thời điểm đo**: 2026-08-17

## Kết quả runtime

| Implementation | Workload | Kết quả | Thời gian | Throughput | Trạng thái |
|---|---:|---|---:|---:|---|
| Legacy JPA | 25.000 proposals | Decision + outbox hoàn tất | 4.139 ms | 6.040 records/s | PASS |
| FT-045 candidate | 25.000 proposals | 1 chunk; decision + outbox + checkpoint hoàn tất | 5.648 ms | 4.426 records/s | PASS |
| Legacy JPA | 1.000.000 proposals | Crash trước khi ghi decision/outbox | Không có | Không có | BLOCKED |
| FT-045 candidate | 1.000.000 proposals | 40 chunks; `APPROVAL_COMMITTED` và decision/outbox assertions hoàn tất | 148.794 ms | 6.721 records/s | PASS |

Lần chạy 25k cũng có warmup 5.925 ms; số dùng để so sánh là measured run 4.139 ms.

Candidate 25k chạy với `chunk-size=25.000`, `jdbc-batch-size=500`, review projection disabled và hoàn tất tại `APPROVAL_COMMITTED`. So với legacy 25k, candidate chậm hơn **1.509 ms (+36,5%)** và throughput thấp hơn **1.614 records/s (-26,7%)**. Candidate có thêm accept, claim, lease và checkpoint nên kết quả này phản ánh chi phí end-to-end của durable operation, không được ghi nhận là cải thiện hiệu năng.

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

## Candidate FT-045 — lần chạy trước khi tách JDBC batch

Candidate đã loại bỏ giant `IN (...)` bằng keyset chunking, nhưng chunk `25.000` bị hủy sau 5 giây tại `insertOutbox()`. Batch entry thứ `11.550/25.000` bị PostgreSQL cancel trong khi kiểm tra FK `scan_outbox_event.operation_id → scan_approval_operation.id`.

Nguyên nhân hiệu năng đã xác định: candidate gửi toàn bộ 25.000 row trong một JDBC batch, trong khi legacy Hibernate dùng batch 500. Sau khi đổi thành `jdbc-batch-size=500`, tắt projection đúng policy và đưa event preparation ra ngoài transaction persistence, candidate hoàn tất workload 25k trong 5.648 ms.

## Candidate FT-045 — workload 1M hoàn tất

Candidate đã xử lý đủ **1.000.000 proposals** bằng `40 × 25.000` chunks trên laptop:

```text
rows=1000000
chunkSize=25000
jdbcBatchSize=500
measuredMs=148794
throughputPerSecond=6721
```

Thời gian tương đương khoảng **2 phút 28,8 giây**. Đây là số liệu end-to-end của candidate gồm accept operation, claim, keyset read, event preparation, decision/outbox persistence, checkpoint và completion. Không so sánh trực tiếp với legacy 1M vì legacy bị block trước persistence bởi giới hạn 65.535 bind parameters.

## Kết luận baseline

Legacy 1M không có runtime baseline vì bị block ở idempotency lookup. Candidate hiện đã có evidence 1M hoàn tất trong 148.794 giây ở môi trường laptop; cần giữ nguyên workload/hardware/config khi so sánh các tối ưu tiếp theo.
