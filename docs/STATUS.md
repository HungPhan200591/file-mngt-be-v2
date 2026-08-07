# Trạng thái Backend V2

Updated: 2026-08-06

## Hiện tại

- Active feature: [`023-file-inventory-seed`](./features/023-file-inventory-seed/03-plan.md) `DONE` (BT-02 File inventory seed).
- Ready feature: [`013-media-worker-processing-foundation`](./features/013-media-worker-processing-foundation/03-plan.md) `READY`: bắt đầu khi quay lại Phase 4.
- Nợ kỹ thuật cần lưu ý: [`TD-004`–`TD-005`](./TECHNICAL_DEBT.md).

## Gate còn mở trước cutover frontend

- **Phase 4:** Media Worker chưa có processing pipeline: technical metadata, thumbnail, GIF, hash, completion event và Catalog update.
- **Phase 7:** Chưa có importer/backfill V1: inventory root, dry-run, batch idempotent, checkpoint và reconciliation.
- **Observability mở rộng:** alert/SLO, profiling sâu và k6.

## Việc kế tiếp

1. Triển khai **BT-03 — Inventory matcher** (SC-01) bỏ qua parse/proposal cho các path không đổi `(fileSize, modifiedAt)` và đánh dấu `MISSING`.
2. Quay lại **FT013 — Media Worker processing foundation** khi quay lại Phase 4.
3. Lập feature Import/backfill V1.
