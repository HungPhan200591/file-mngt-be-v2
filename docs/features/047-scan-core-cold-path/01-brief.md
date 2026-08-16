# FT-047 — SC-01 Scan-Core Cold Path Without Diff Stage

Status: `READY`  
Owner: `scan-service`  
Dependency: [FT-046](../046-scan-core-pipeline-optimization/03-plan.md)  
Use case: [SC-01 Approve 1M Context](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md)

## 1. Mục tiêu

Tối ưu lần scan đầu của root rỗng bằng cách giữ `scan_inventory_stage` làm snapshot nhưng bỏ bước materialize vào `scan_inventory_diff_stage`. Cold path đọc trực tiếp snapshot để parse và ghi inventory.

## 2. Acceptance criteria

1. Cold root không tạo row trong `scan_inventory_diff_stage`.
2. Cold path vẫn ghi đủ inventory, proposal và issue với cardinality chính xác.
3. Warm path giữ nguyên materialized diff và semantics unchanged/changed/revived/missing.
4. Cold failure sau bất kỳ chunk nào vẫn cleanup/retry được và không tạo completion giả.
5. Lease fence, checkpoint và chunk transaction giữ nguyên invariant hiện tại.
6. Benchmark cold path được so sánh với FT-046 baseline trên cùng fixture.

## 3. Ngoài phạm vi

- Stream trực tiếp từ filesystem vào business persistence.
- Thay đổi warm SQL diff.
- Producer-consumer overlap hoặc nhiều DB connection song song.
- Thay đổi REST, Kafka, Catalog hoặc Query contract.
