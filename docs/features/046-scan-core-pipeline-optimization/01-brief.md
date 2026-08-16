# FT-046 — SC-01 Scan-Core Pipeline Optimization & Benchmark Evidence

Status: `READY`  
Owner: `scan-service`  
Use case: [SC-01 Approve 1M Context](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md)  
Related work: [BT-09](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned), [FT-031](../031-scan-reconciliation-persistence-optimization/03-plan.md)

## 1. Mục tiêu

Thiết lập bằng chứng đo được cho `scan-core` 1.000.000 records và tối ưu theo từng biến độc lập:

- Ghi telemetry chính xác cho discovery, diff, parse, persistence và finalize.
- Có benchmark cold/warm phản ánh đúng các workload quan trọng.
- So sánh semantic-equivalent SQL diff và giữ phương án có lợi ích runtime ổn định.
- Làm cơ sở cho cold path và producer-consumer pipeline ở feature tiếp theo.

## 2. Acceptance criteria

1. Benchmark phân biệt được cold root, warm unchanged, warm incremental và warm full-change.
2. Mỗi run ghi `discoveryMs`, `diffMs`, `parseMs`, các persistence phase, `finalizeMs` và `totalMs`.
3. Kết quả diff của SQL mới khớp SQL hiện tại cho new, changed, revived và unchanged records.
4. Có row-count, cleanup, retry/failure và lease-fence assertions cho full pipeline.
5. Report ghi workload, hardware/runtime envelope, median/min/max và scope `scan-core`; không gọi là cross-service SLO.
6. Không thay đổi REST, Kafka, database ownership hoặc approval event contract.

## 3. Ngoài phạm vi

- Bỏ hoàn toàn `scan_inventory_stage` hoặc `scan_inventory_diff_stage`.
- Producer-consumer overlap hoặc nhiều DB writer.
- Thay đổi BT-09B decision/outbox, Catalog, Query hoặc `QUERY_DB_READY`.
- Sửa [07-performance-slo-and-benchmarks.md](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/07-performance-slo-and-benchmarks.md).
