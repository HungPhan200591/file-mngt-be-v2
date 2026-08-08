# Trạng thái Backend V2

Updated: 2026-08-08

## Hiện tại

- Active SC-01: BT-02/BT-03 `DONE`; [FT-025.4 materialized diff staging](./features/025-inventory-staging-reconciliation/03-plan.md) đã loại bỏ các full staging pass lặp lại trong warm reconciliation, chờ migration và benchmark được người dùng cho phép.
- Active SC-01: [FT-026 scan run liveness guard](./features/026-scan-run-liveness-guard/03-plan.md) đã implement timeout PostgreSQL cục bộ và delayed lease deadline; chờ verification được người dùng cho phép.
- Active SC-01: [FT-027 scan run SSE progress](./features/027-scan-run-sse-progress/03-plan.md) `DONE`; SSE aggregate BE/Gateway và FE REST-first/SSE-primary đã implement, chờ verification runtime được người dùng cho phép.
- Active SC-01: [FT-028 parallel reconciliation pipeline](./features/028-parallel-reconciliation-pipeline/03-plan.md) đã implement hybrid write path: parallel analyzer → direct PostgreSQL `COPY` vào `scan_proposal`/`scan_issue`, set-based write từ diff stage vào `scan_file_inventory`, checkpoint cùng transaction; PostgreSQL 18 + volume `postgres-data` mới + UUIDv7. V13 chỉ bỏ hai FK COPY hot path proposal/issue → run, giữ FK decision/outbox → proposal; V7 Catalog và V4 Query mở default native UUIDv7. FE không pull result list khi `RUNNING`; resume sau restart deferred. Chưa chạy verification/benchmark end-to-end.
- Active Foundation: [FT-029 async logging foundation](./features/029-async-non-blocking-logging-foundation/03-plan.md) `IMPLEMENTED — pending runtime verification`; 5 microservices ghi console và JSON file qua AsyncAppender (`queueSize=16384`, `neverBlock=false`), scan worker đặt `runId` vào MDC. Cần xác nhận startup và file JSON của từng service trước khi đóng feature.
- Active SC-01: [FT-030 scan performance telemetry](./features/030-scan-performance-telemetry/03-plan.md) `IMPLEMENTED — pending runtime verification`; terminal timeline phát theo `runId`, console và ECS JSON file dùng async logging lossless.
- Active SC-01: [FT-031 scan reconciliation persistence optimization](./features/031-scan-reconciliation-persistence-optimization/03-plan.md) FT-031.1 `IMPLEMENTED — pending runtime evidence`: structured timing per chunk sau transaction completion và terminal aggregate phân biệt committed/rolled-back; 31.2 buffered COPY chỉ mở sau benchmark baseline.
- Ready feature: [`013-media-worker-processing-foundation`](./features/013-media-worker-processing-foundation/03-plan.md) `READY`: bắt đầu khi quay lại Phase 4.
- Nợ kỹ thuật cần lưu ý: [`TD-004`–`TD-005`](./TECHNICAL_DEBT.md).

## Gate còn mở trước cutover frontend

- **Phase 4:** Media Worker chưa có processing pipeline: technical metadata, thumbnail, GIF, hash, completion event và Catalog update.
- **Phase 7:** Chưa có importer/backfill V1: inventory root, dry-run, batch idempotent, checkpoint và reconciliation.
- **Observability mở rộng:** alert/SLO, profiling sâu và k6.

## Việc kế tiếp

1. Khi được cho phép, verify và benchmark FT-028 hybrid write path với 1M file; ghi kết quả production-like vào benchmark folder.
2. Khi được cho phép, chạy verification SSE qua Gateway: frame progress trước terminal, heartbeat >30 giây, reconnect/fallback và stale `404`.
3. Khi được cho phép, chạy verification Scan Service và benchmark cold/warm sau FT-025.3/FT-026.
4. Triển khai **BT-04 — Catalog batch existence API** (SC-01): internal API nhận tối đa 500 candidate, trả classification.
