# FT-051 — SC-01 BT-09B: Logical Approval Sharding

Status: `IMPLEMENTED — SMOKE VERIFIED; 1M BENCHMARK PENDING`  
Owner: `scan-service`

## Mục tiêu

Mở rộng durable approval từ một DB writer sang `1..N` logical shard worker. Shard là partition hash ổn định
của `scan_proposal.id`, không tạo business table mới và không thay Kafka contract.

## Những gì đã apply

- `scan_approval_operation_shard` giữ shard number/count, cursor, counter, lease và retry state.
- Operation accept khởi tạo đúng `shardCount` shard trong cùng transaction.
- Worker claim shard bằng `FOR UPDATE SKIP LOCKED`; các virtual thread worker chạy song song trên cùng node.
- Mỗi shard lọc proposal bằng partition hash + `proposalCutoffId`, có cursor/checkpoint/lease riêng.
- Decision, outbox và shard checkpoint vẫn atomic trong transaction chunk.
- Parent chỉ chuyển `APPROVAL_COMMITTED` khi mọi shard complete và tổng committed count khớp expected count.

## Ngoài phạm vi

- Multi-node qualification, Catalog/Query readiness và shard-aware downstream observability.
- Physical PostgreSQL partitioning hoặc nhiều business tables.

## Benchmark cần chạy

So sánh cùng fixture: `shardCount=1,2,4`, kết hợp `preparationParallelism=1,2,4`. Đo elapsed time,
throughput, PostgreSQL WAL/IOPS, lock wait, pool wait và outbox backlog.

## Evidence hiện có

Smoke benchmark đã pass với `1,000` rows, `shardCount=4`, `copyEnabled=true`,
`preparationParallelism=4`: bốn shard đều `COMPLETED`, parent đạt `APPROVAL_COMMITTED`, decision/outbox
và parent committed count đều đủ `1,000`. Số `320 ms` của smoke không dùng để suy luận throughput 1M.
