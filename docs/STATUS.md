# Trạng thái Backend V2

Updated: 2026-08-08

## Hiện tại

- SC-01 Scan API và persistence hot path đã hoàn tất. [FT-028](./features/028-parallel-reconciliation-pipeline/03-plan.md) dùng parallel analyze, direct PostgreSQL `COPY` cho proposal/issue, reconciliation set-based và checkpoint lease-fenced; PostgreSQL 18 + UUIDv7 đã mở cho Scan/Catalog/Query. V13 chỉ bỏ hai FK proposal/issue → run; FK decision/outbox → proposal vẫn giữ `ON DELETE CASCADE`.
- [FT-030 telemetry](./features/030-scan-performance-telemetry/03-plan.md) đã có runtime evidence: terminal timeline và chunk persistence event đọc được ở console theo `runId`, đồng thời ghi ECS JSON không trùng MDC key.
- [FT-031 persistence optimization](./features/031-scan-reconciliation-persistence-optimization/03-plan.md) `DONE`: buffer COPY đã thử rồi rollback vì không có lợi ích; cold inventory path dùng `INSERT ... SELECT`. Run `019fe018-7640-7ff9-b467-c855a050f963` xử lý 1M file, commit 10/10 chunk trong `25.763s` (`inventoryWriteMs=3.908s`), đạt mục tiêu dưới 30 giây. Warm root giữ upsert để bảo toàn changed/revived semantics; không đổi chunk size mặc định khi chưa có benchmark kiểm soát.
- Các verification không chặn task tiếp theo được giữ deferred ở Plan owner: FT-025 semantics Testcontainers, FT-026 timeout/lease-loss, FT-027 E2E Gateway/SSE. [FT-029 async logging](./features/029-async-non-blocking-logging-foundation/03-plan.md) chỉ còn cần xác nhận runtime cho bốn service ngoài Scan.
- Ready feature: [`013-media-worker-processing-foundation`](./features/013-media-worker-processing-foundation/03-plan.md) `READY`: bắt đầu khi quay lại Phase 4.
- Nợ kỹ thuật cần lưu ý: [`TD-004`–`TD-005`](./TECHNICAL_DEBT.md).

## Gate còn mở trước cutover frontend

- **Phase 4:** Media Worker chưa có processing pipeline: technical metadata, thumbnail, GIF, hash, completion event và Catalog update.
- **Phase 7:** Chưa có importer/backfill V1: inventory root, dry-run, batch idempotent, checkpoint và reconciliation.
- **Observability mở rộng:** alert/SLO, profiling sâu và k6.

## Việc kế tiếp ưu tiên

1. Triển khai [FT-013 Media Worker processing foundation](./features/013-media-worker-processing-foundation/03-plan.md) cho Phase 4.
2. Triển khai **BT-04 — Catalog batch existence API** (SC-01): internal API nhận tối đa 500 candidate, trả classification.
3. Khi cần harden trước cutover, thực hiện các verification deferred theo Plan owner; không mở lại tuning persistence nếu chưa có benchmark hypothesis mới.
