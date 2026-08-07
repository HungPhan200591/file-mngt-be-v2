# Trạng thái Backend V2

Updated: 2026-08-07

## Hiện tại

- Active SC-01: BT-02/BT-03 `DONE`; [FT-025.2 streaming staging reconciliation](./features/025-inventory-staging-reconciliation/03-plan.md) đã implement segment 500.000 và set-based diff, chờ verification/benchmark được người dùng cho phép.
- Ready feature: [`013-media-worker-processing-foundation`](./features/013-media-worker-processing-foundation/03-plan.md) `READY`: bắt đầu khi quay lại Phase 4.
- Nợ kỹ thuật cần lưu ý: [`TD-004`–`TD-005`](./TECHNICAL_DEBT.md).

## Gate còn mở trước cutover frontend

- **Phase 4:** Media Worker chưa có processing pipeline: technical metadata, thumbnail, GIF, hash, completion event và Catalog update.
- **Phase 7:** Chưa có importer/backfill V1: inventory root, dry-run, batch idempotent, checkpoint và reconciliation.
- **Observability mở rộng:** alert/SLO, profiling sâu và k6.

## Việc kế tiếp

1. Khi được cho phép, chạy verification Scan Service và benchmark cold/warm sau FT-025.2.
2. Triển khai **BT-04 — Catalog batch existence API** (SC-01): internal API nhận tối đa 500 candidate, trả classification.
3. Quay lại **FT013 — Media Worker processing foundation** khi quay lại Phase 4.
