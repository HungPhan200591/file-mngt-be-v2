# Trạng thái Backend V2

Updated: 2026-08-12

## Gate mới nhất — production readiness review

Review tĩnh toàn backend tại commit `45adade8d67c` kết luận **`NOT READY` cho
production/cutover**. Blocker hiện tại là security/network boundary, restart
recovery của scan, blocking filesystem liveness, lease fencing của durable job,
outbox lease/throughput và Query DLT observation/replay evidence. Xem [review đầy đủ](./reviews/2026-08-12-backend-quality-architecture-production-readiness.md)
và [technical debt snapshot](./TECHNICAL_DEBT.md).

## Trọng tâm hiện tại — SC-01 thông luồng

Workstream kế tiếp cho approve quy mô lớn là [SC-01 approve 1M context](../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md),
với break task owner là [BT-09](../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned).
Workload đích là 1.000.000 records tới `QUERY_DB_READY`; review 5.000 records chỉ là calibration và
approve 1M chưa có runtime evidence hoặc SLO chính thức.

[FT-034 — Catalog batch existence API](./features/034-catalog-batch-existence-api/01-brief.md) và
[FT-035 — Scan–Catalog filtering](./features/035-scan-catalog-filtering/01-brief.md) đã thông luồng
BT-04 → BT-05 của SC-01, đều ở trạng thái **`IMPLEMENTED — verification deferred`**.
[FT-036 — Event contract/DLT alignment](./features/036-event-contract-dlt-alignment/01-brief.md),
[FT-037 — Outbox backlog capacity](./features/037-outbox-backlog-capacity/01-brief.md),
[FT-038 — Targeted issue recheck](./features/038-targeted-issue-recheck/01-brief.md) và
[FT-039 — Durable bulk decision](./features/039-durable-bulk-decision/01-brief.md) đã có code,
đều ở trạng thái **`IMPLEMENTED — verification deferred`**.
[FT-040 — Primary video tag ownership](./features/040-primary-video-tag-ownership/01-brief.md) và
[FT-041 — Scan rerun overwrite](./features/041-scan-rerun-overwrite/01-brief.md) đã sửa luồng
repair metadata không cần reset data, đồng thời tạo proposal xóa cho file biến mất và dọn
Catalog/Query/Redis/Elasticsearch sau approve; đều ở trạng thái **`IMPLEMENTED — verification deferred`**.
[FT-042 — Primary video election](./features/042-primary-video-election/01-brief.md) đã chuyển quyền bầu
`PRIMARY_VIDEO` về Catalog, lưu tag theo video asset và dùng comparator ưu tiên video không tag;
implementation hiện **`DONE — static verification only`**.

[FT-043 — Video Gallery và throughput event](./features/043-video-gallery-throughput/01-brief.md) dùng card theo video
asset/root, fallback một card theo subject cho root chỉ có ảnh/GIF, trả đủ preview của subject, tag theo asset và detail
theo subject; đồng thời batch hóa Kafka acknowledgement, bulk update outbox và cấu hình nhiều partition/consumer lane.
Trạng thái **`DONE — targeted Query integration verified`**; topic hiện hữu, migration, DLT replay và benchmark runtime
chưa chạy.

Phạm vi đã có Catalog provider và Scan consumer cho SC-01 BT-04/BT-05:

- Internal read-only `POST /internal/v2/catalog/scan-existence`, batch từ 1 đến 500 candidate.
- Lookup set-based locator `storageKey + relativePath` và canonical subject identity trong `catalog_db`.
- Trả bốn classification; không tạo subject/asset/outbox. Scan gọi ngoài transaction persistence, split
  batch tối đa 500, exact skip proposal và giữ evidence cho các classification còn lại.
- Có Flyway unique partial index locator non-null; migration phải fail nếu data conflict, không tự
  cleanup/import dữ liệu.
- Gateway route `/api/v2/scans/**` đã bao phủ các endpoint SC-01 mới; contract routing và integration coverage
  cho bulk decision/recheck đã được cập nhật. FE V2 đã chuyển bulk action sang durable job và thêm recheck issue.
  Runtime E2E/verification vẫn deferred theo ưu tiên thông luồng.
- BT-08A ghi contract v2, explicit event validation và quan sát v2 DLT. BT-08B claim outbox bounded có
  lease/`SKIP LOCKED` cho cả Scan và Catalog, publish ngoài transaction và metrics backlog.
- BT-06C hỗ trợ enqueue targeted recheck theo `issueId`, worker lease và observation mới; BT-07 hỗ trợ
  decision/reopen bulk dạng durable job, bounded batch và progress.

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

Xem [TECHNICAL_DEBT.md](./TECHNICAL_DEBT.md). Snapshot này chỉ giữ gate và
work active; evidence/remediation chi tiết nằm ở review và Feature Plan owner.

## Việc tiếp theo theo thứ tự ưu tiên

1. Ưu tiên P0: security boundary, scan restart/I/O liveness và durable-job fencing.
2. Sau P0, chạy direct verification SC-01: Flyway/index, Testcontainers, Kafka
   DLT, `SKIP LOCKED`, lease reclaim, duplicate và rolling restart.
3. Sau verification, chốt E2E Gateway/FE qua `18100`, SLO/alert và benchmark
   approve → Catalog → Query.
