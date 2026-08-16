# FT-048 — SC-01 Scan-Core Pipelined Reconciliation

Status: `READY`  
Owner: `scan-service`  
Dependency: [FT-047](../047-scan-core-cold-path/03-plan.md)  
Use case: [SC-01 Approve 1M Context](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md)

## 1. Mục tiêu

Overlap CPU analyze của chunk kế tiếp với PostgreSQL commit của chunk hiện tại bằng bounded producer-consumer pipeline, nhưng vẫn giữ một transaction atomic cho mỗi chunk.

## 2. Acceptance criteria

1. Queue có capacity hữu hạn, mặc định 1–2 chunk.
2. Producer không giữ toàn bộ 1M records trong heap.
3. Một DB consumer commit theo thứ tự chunk.
4. Inventory, proposal, issue và checkpoint của một chunk vẫn cùng transaction.
5. Producer/consumer failure, cancellation, lease expiry và shutdown đều về terminal state.
6. Pipeline không làm tăng duplicate, out-of-order commit hoặc checkpoint regression.
7. Chỉ chấp nhận nếu benchmark cho thấy lợi ích ổn định so với FT-047.

## 3. Ngoài phạm vi

- Nhiều DB writer hoặc nhiều transaction cho một chunk.
- Thay đổi batch size mặc định chỉ vì kỳ vọng hiệu năng.
- Thay đổi REST/Kafka/Catalog/Query contract.
