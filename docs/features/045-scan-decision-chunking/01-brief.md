# FT-045 — SC-01 BT-09B: Durable Scan Approval & Decision Chunking

Owner: `scan-service`  
Use case: [SC-01 Approve 1M Context](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md) — [BT-09B](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned)

## 1. Mục tiêu

- Hiện thực operation contract của FT-044: commit operation row `ACCEPTED` trong `scan_db`, trả HTTP `202`
  cùng `operationId`, sau đó xử lý nền không giữ HTTP request.
- Thay run-wide JPA `findByScanRunId()` + `saveAll()` bằng keyset chunk tối đa 25.000 proposal và JDBC
  bounded write. Chỉ giữ một chunk trong heap, không hydrate toàn bộ 1.000.000 entity.
- Ghi `scan_decision`, `scan_outbox_event` và durable operation checkpoint trong cùng transaction chunk.
- Đưa `operationId` và `batchId` vào durable event payload/outbox để BT-09C–BT-09E có thể đếm,
  dedupe và replay theo operation.
- Giữ phase budget Scan decision + outbox commit ở mức `≤ 5s` P95. Đây là budget allocation; FT-045
  chưa được coi là đạt cho tới khi BT-09G có repeated runtime evidence.

## 2. Acceptance criteria

1. `POST /api/v2/scans/runs/{scanRunId}/approve` khóa run, đếm proposal `PENDING`, commit operation
   `ACCEPTED` O(1) và trả `202 ApprovalOperationAccepted`.
2. Mỗi scan run chỉ có một operation ở trạng thái `ACCEPTED`/`RUNNING`; duplicate concurrent request trả
   `409`. Single/review decision xung đột không được làm sai cardinality operation.
3. Worker claim bằng `FOR UPDATE SKIP LOCKED`, có lease riêng của approval operation và reclaim được sau
   restart. Worker stale không cập nhật checkpoint hoặc terminal state sau khi mất lease.
4. Mỗi chunk đọc keyset tối đa 25.000 proposal chưa có decision; cardinality `0`, `1`, full batch,
   exact-multiple và final partial batch đều có terminal path rõ ràng.
5. `scan_decision` + data outbox + operation checkpoint commit atomic trong một transaction
   `REQUIRES_NEW`; event ID được dùng chung ở decision và outbox.
6. Mỗi event do operation tạo mang `operationId` và deterministic `batchId`; payload vẫn giữ đúng event
   type, partition key và semantic metadata hiện hành.
7. Chỉ chuyển local operation state sang `APPROVAL_COMMITTED` khi
   `scanCommittedRecordCount = expectedRecordCount`; mismatch chuyển retry/`FAILED`, không báo thành công giả.
8. `GET /api/v2/scans/operations/{operationId}/status` đọc durable status từ `scan_db`.
9. Retry/reclaim không tạo duplicate decision/outbox. Opposite decision là conflict, không bị
   `ON CONFLICT DO NOTHING` che mất.

## 3. Ngoài phạm vi

- Continuous bounded outbox drain và Kafka `APPROVAL_COMMITTED` manifest (`BT-09C`).
- Catalog coalesce, operation-scoped consumer counter (`BT-09D`).
- Query bulk projection (`BT-09E`) và DLT/replay qualification (`BT-09F`).
- Benchmark/khẳng định đạt P95/P99 (`BT-09G`).
- Thay đổi semantics single decision, reject/reopen và review-queue job ngoài guard chống xung đột với
  active approval operation.
