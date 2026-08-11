# Trạng thái Backend V2

Updated: 2026-08-11

## Trọng tâm hiện tại — FT-034

[FT-034 — Catalog batch existence API](./features/034-catalog-batch-existence-api/01-brief.md) đã có code
Catalog provider, nhưng ở trạng thái **`IMPLEMENTED — verification deferred`**.

Phạm vi chỉ là Catalog provider cho SC-01 BT-04:

- Internal read-only `POST /internal/v2/catalog/scan-existence`, batch từ 1 đến 500 candidate.
- Lookup set-based locator `storageKey + relativePath` và canonical subject identity trong `catalog_db`.
- Trả bốn classification; không tạo subject/asset/outbox và chưa thêm Scan client.
- Có Flyway unique partial index locator non-null; migration phải fail nếu data conflict, không tự
  cleanup/import dữ liệu.
- Chưa có Scan client, Gateway route hay mapping FE. Direct Catalog integration test/migration verification
  được deferred theo ưu tiên thông luồng; BT-05 không được mở trước evidence này.

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

1. Khi người dùng ưu tiên hardening, chạy direct verification FT-034: Flyway/index, Catalog Testcontainers,
   snapshot/conflict/read-only và query-count evidence.
2. Chỉ sau evidence FT-034 mới mở BT-05 Scan–Catalog filtering; mapping FE vẫn thuộc feature consumer,
   không thuộc internal Catalog provider.
3. Khi ưu tiên verification, chạy FT-033 Testcontainers/benchmark đã deferred.
