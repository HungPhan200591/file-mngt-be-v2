# Trạng thái Backend V2

Updated: 2026-08-04

## Hiện tại

- Active discovery: [`FT018`](./features/018-scan-semantic-rule-normalization/00-discovery.md) đang chốt semantic rule; chưa có Design/Plan/code.
- Feature kế tiếp: [`013-media-worker-processing-foundation`](./features/013-media-worker-processing-foundation/03-plan.md) đang `READY`; bắt đầu khi chủ dự án sẵn sàng quay lại Phase 4.
- Không tạo Plan trạng thái tạm dừng. [`012-gallery-v2-parity-foundation`](./features/012-gallery-v2-parity-foundation/03-plan.md) vẫn `DRAFT` và chỉ triển khai sau backend parity.
- Debt cần lưu ý khi chạm owner: [`TD-002`–`TD-005`](./TECHNICAL_DEBT.md); riêng `TD-003` đã khoanh vùng tại `query-service`, không phải `scan-service`.

## Gate còn mở trước cutover frontend

- **Phase 4:** Media Worker chưa có processing pipeline: technical metadata, thumbnail, GIF, hash, completion event và Catalog update.
- **Phase 7:** chưa có importer/backfill V1: inventory root, dry-run, batch idempotent, checkpoint và reconciliation.
- **Observability mở rộng:** OpenTelemetry trace xuyên Kafka, alert/SLO, profiling sâu và k6 nằm ngoài FT014; không chặn FT013.

## Việc kế tiếp

1. Dùng dashboard/log để đọc và debug lại các flow 002–011 cho đến khi chủ dự án nắm vững.
2. Khi chủ dự án sẵn sàng, quay lại **FT013 — Media Worker processing foundation**; sau đó mới lập feature Import/backfill V1.
