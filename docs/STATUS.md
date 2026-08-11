# Trạng thái Backend V2

Updated: 2026-08-08

## Trọng tâm hiện tại — FT-034

[FT-034 — Catalog batch existence API](./features/034-catalog-batch-existence-api/01-brief.md) đã có
Brief/Design/Plan và OpenAPI ở trạng thái `READY`, **chưa triển khai code**.

Phạm vi chỉ là Catalog provider cho SC-01 BT-04:

- Internal read-only `POST /internal/v2/catalog/scan-existence`, batch từ 1 đến 500 candidate.
- Lookup set-based locator `storageKey + relativePath` và canonical subject identity trong `catalog_db`.
- Trả bốn classification; không tạo subject/asset/outbox và chưa thêm Scan client.
- Thêm unique partial index locator non-null khi triển khai; migration phải fail nếu data conflict, không
  tự cleanup/import dữ liệu.

## Trạng thái đã ổn định

- SC-01 Scan API và persistence hot path của [FT-028](./features/028-parallel-reconciliation-pipeline/03-plan.md)
  đã có parallel analyze, direct `COPY`, set-based reconciliation và checkpoint lease-fenced.
- [FT-030 telemetry](./features/030-scan-performance-telemetry/03-plan.md) đã có runtime evidence cho
  terminal timeline và chunk persistence theo `runId`.
- [FT-031 persistence optimization](./features/031-scan-reconciliation-persistence-optimization/03-plan.md)
  đã benchmark run 1M file dưới 30 giây; không tuning lại chunk size nếu chưa có hypothesis/evidence mới.
- [FT-032 Scan review queue](./features/032-scan-review-queue/03-plan.md) có code tối thiểu ở trạng thái `DONE`.
- [FT-033 Scan review read model](./features/033-scan-review-read-model/03-plan.md) đã hoàn tất code cho
  generation projection, durable worker, fallback read và decision synchronization. Implementation review
  `CONDITIONAL`: compile/Testcontainers/migration/runtime evidence được deferred theo yêu cầu người dùng.
- [FT-013 Media Worker processing foundation](./features/013-media-worker-processing-foundation/03-plan.md)
  vẫn `READY`, nhưng không phải trọng tâm của session hiện tại.

## Deferred và gate rộng hơn

- FT-033 còn gate verification trước production cutover: migration/Testcontainers, decision race, stale lease,
  query plan và benchmark 1M dưới projector load; xem
  [implementation review](./features/033-scan-review-read-model/06-implementation-review.md).
- Verification deferred: FT-025 semantics Testcontainers, FT-026 timeout/lease-loss, FT-027 E2E
  Gateway/SSE; thực hiện theo Plan owner khi có scope hardening phù hợp.
- Phase 4 còn thiếu Media Worker processing pipeline; Phase 7 còn thiếu importer/backfill V1.
- Observability mở rộng còn thiếu alert/SLO, profiling sâu và k6.

## Nợ kỹ thuật đang mở

Xem [TECHNICAL_DEBT.md](./TECHNICAL_DEBT.md) — hiện còn TD-004, TD-005 và TD-006. STATUS chỉ giữ
liên kết snapshot; chi tiết remediation nằm ở debt/feature owner.

## Việc tiếp theo theo thứ tự ưu tiên

1. Review/chấp thuận contract và decision table của FT-034; chưa cần sửa code nếu chỉ đang chốt feature.
2. Khi người dùng yêu cầu, triển khai riêng Catalog provider FT-034 và direct integration test; không kéo
   Scan client vào cùng scope.
3. Khi ưu tiên verification, chạy FT-033 Testcontainers/benchmark đã deferred; chỉ sau evidence FT-034 mới
   mở BT-05 Scan–Catalog filtering.
