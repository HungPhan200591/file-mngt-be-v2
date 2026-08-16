# FT-049 — SC-01 Scan-Core Scale Qualification

Status: `READY`  
Owner: `scan-service`  
Dependencies: [FT-046](../046-scan-core-pipeline-optimization/03-plan.md), [FT-047](../047-scan-core-cold-path/03-plan.md), [FT-048](../048-scan-core-pipelined-reconciliation/03-plan.md)  
Use case: [SC-01 Approve 1M Context](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md)

## 1. Mục tiêu

Chạy qualification cuối cho `scan-core` theo scale ladder và workload matrix, xác định phase bottleneck, capacity ceiling và SLO evidence trong hardware envelope đã ghi nhận.

## 2. Acceptance criteria

1. Chạy cùng implementation qua 1K, 5K, 50K, 250K và 1M records.
2. Tách cold, warm unchanged, warm incremental và warm full-change.
3. Báo p50/p95/p99 hoặc nêu rõ giới hạn mẫu đo, cùng throughput và phase timing.
4. Ghi CPU, memory, PostgreSQL container/config, WAL/IOPS nếu quan sát được, pool wait và queue wait.
5. Có correctness gate cho row counts, cleanup, retry, duplicate và terminal state ở 1M.
6. Kết luận chỉ áp dụng cho `scan-core`; không gọi là Scan → Catalog → Query SLO.

## 3. Ngoài phạm vi

- Đo Catalog, Kafka, Query, Redis, Search hoặc `QUERY_DB_READY`.
- Sửa SLO contract trong file 07.
- Thay đổi implementation trong lúc đang thu thập một run.
