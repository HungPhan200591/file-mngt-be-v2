# FT-050 — SC-01 BT-09B: Approval Preparation & Persistence Acceleration

Owner: `scan-service`  
Dependency: [FT-045](../045-scan-decision-chunking/03-plan.md)  
Architecture: [SC-01 end-to-end architecture](../../architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md)

## 1. Mục tiêu

Tăng throughput của local Scan approval persistence cho workload 1.000.000 proposal mà vẫn giữ một
commit lane và transactional outbox atomic:

- Chuẩn bị event DTO/JSON theo partition trên virtual thread có giới hạn song song.
- Bulk-validate `DELETE_ASSET` theo một query/chunk, loại bỏ lookup inventory N+1.
- Ghi decision và outbox bằng PostgreSQL `COPY`, vẫn có JDBC batch fallback để rollback/tối chiếu.
- Chốt `proposalCutoffId` khi accept operation và dùng cutoff đó trong mọi keyset page.
- Bổ sung index cho keyset `scan_run_id + proposal.id` và benchmark lại đúng boundary
  `APPROVAL_COMMITTED`.

## 2. Acceptance criteria

1. Mỗi chunk tối đa 25.000 proposal; không preload dữ liệu/payload của toàn bộ operation vào heap.
2. Preparation được chia thành tối đa `preparationParallelism` partition; kết quả merge giữ thứ tự cursor
   và cardinality đúng một event/decision cho mỗi proposal.
3. `DELETE_ASSET` dùng đúng một bulk inventory lookup/chunk; proposal stale vẫn fail closed trước khi
   persistence transaction bắt đầu.
4. `scan_decision`, `scan_outbox_event` và checkpoint vẫn commit/rollback cùng `REQUIRES_NEW` transaction.
5. `COPY` là default persistence path; `copy-enabled=false` dùng JDBC batch 500 để rollback hoặc A/B benchmark.
6. Keyset query có index `(scan_run_id, id)`; quyết định giữ index dựa trên `EXPLAIN (ANALYZE, BUFFERS)` và
   benchmark scan-core/approval, không chỉ dựa vào giả định.
7. Benchmark ghi rõ chunk size, JDBC fallback setting, preparation parallelism, elapsed time và throughput.
8. Không đổi REST API, Kafka event schema, partition key, database ownership hoặc semantics watermark.
9. Operation cũ sau migration được backfill cutoff khi còn proposal; operation mới luôn lưu cutoff durable.

## 3. Ngoài phạm vi

- Logical shard ledger và nhiều DB writer cho cùng operation.
- Outbox continuous drain/backpressure (`BT-09C`), Catalog coalesce (`BT-09D`) hoặc Query projection
  (`BT-09E`).
- Khẳng định SLO `QUERY_DB_READY`, p95/p99 hoặc production sizing; các việc đó thuộc `BT-09G`.
