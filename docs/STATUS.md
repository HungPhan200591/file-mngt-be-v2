# Trạng thái Backend V2

Updated: 2026-08-11

## Trọng tâm hiện tại — FT-036/037

[FT-034 — Catalog batch existence API](./features/034-catalog-batch-existence-api/01-brief.md) và
[FT-035 — Scan–Catalog filtering](./features/035-scan-catalog-filtering/01-brief.md) đã thông luồng
BT-04 → BT-05 của SC-01, đều ở trạng thái **`IMPLEMENTED — verification deferred`**.
[FT-036 — Event contract/DLT alignment](./features/036-event-contract-dlt-alignment/01-brief.md) và
[FT-037 — Outbox backlog capacity](./features/037-outbox-backlog-capacity/01-brief.md) đã có code,
cũng **`IMPLEMENTED — verification deferred`**.

Phạm vi đã có Catalog provider và Scan consumer cho SC-01 BT-04/BT-05:

- Internal read-only `POST /internal/v2/catalog/scan-existence`, batch từ 1 đến 500 candidate.
- Lookup set-based locator `storageKey + relativePath` và canonical subject identity trong `catalog_db`.
- Trả bốn classification; không tạo subject/asset/outbox. Scan gọi ngoài transaction persistence, split
  batch tối đa 500, exact skip proposal và giữ evidence cho các classification còn lại.
- Có Flyway unique partial index locator non-null; migration phải fail nếu data conflict, không tự
  cleanup/import dữ liệu.
- Không mở Gateway route hay mapping FE trong lát này. Direct Catalog/Scan integration, migration, timeout,
  protocol mismatch và E2E verification được deferred theo ưu tiên thông luồng.
- BT-08A ghi contract v2, explicit event dispatch và quan sát cả v1/v2 DLT. BT-08B claim outbox bounded có
  lease/`SKIP LOCKED` cho cả Scan và Catalog, publish ngoài transaction và metrics backlog.

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

1. Khi người dùng ưu tiên hardening, chạy direct verification FT-034/035/036/037: Flyway/index, Catalog/Scan
   Testcontainers, Kafka contract/DLT, `SKIP LOCKED`, lease reclaim và duplicate evidence.
2. Mở feature Gateway/FE mapping riêng sau khi behavior backend có evidence; không gộp vào internal API.
3. Khi ưu tiên verification, chạy FT-033 Testcontainers/benchmark đã deferred.
