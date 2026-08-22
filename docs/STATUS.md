# Trạng thái Backend V2

Updated: 2026-08-22

## Gate mới nhất — Production Readiness Review

Review tĩnh toàn backend tại commit `45adade8d67c` kết luận **`NOT READY` cho production/cutover**.
Blocker hiện tại là security/network boundary, restart recovery của scan, blocking filesystem liveness, lease fencing của durable job, outbox lease/throughput và Query DLT observation/replay evidence.
Xem [review đầy đủ](./reviews/2026-08-12-backend-quality-architecture-production-readiness.md) và [technical debt snapshot](./TECHNICAL_DEBT.md).

---

## Trọng tâm ưu tiên tối đa hiện tại — SC-01 BT-09: Approve 1M records to `QUERY_DB_READY`

Toàn bộ tài nguyên và session hiện tại tập trung tuyệt đối vào workstream [SC-01 approve 1M context](../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md) theo break-task [BT-09](../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned).

Mục tiêu cốt lõi: Thông luồng và tối ưu pipeline approve **1.000.000 records** sau khi proposal đã tồn tại trong Scan:
```text
Scan decision/outbox → Kafka → Catalog batch/coalesce → Kafka → Query bulk projection → QUERY_DB_READY
```

SLO đã rebudget ngày 2026-08-22: `QUERY_DB_READY` P95 `<= 60s`; riêng Catalog từ first receive tới final
output broker ack phải đạt tối thiểu `30.000 input records/s`, stretch `40.000 input records/s`.

### Roadmap triển khai BT-09 theo thứ tự:
1. **`BT-09A — Operation contract`**: **`DONE`** (Đã chốt tại [FT-044](./features/044-approve-1m-operation-contract/01-brief.md), [operation watermark](./contracts/events/media.approval.watermark.v1.md) và [subject snapshot v2](./contracts/events/media.subject.changed.v2.md)).
2. **`BT-09B — Scan decision/outbox` (`IMPLEMENTED — verification deferred`, FT-045/FT-050/FT-051)**: Durable approval operation, decision + outbox atomic theo bounded chunk tối đa 25.000 items, checkpoint/lease fence, proposal cutoff, bounded preparation, COPY/JDBC fallback và logical shard ledger. Một local benchmark FT-051 ghi nhận 30.759 ms cho 1M với 4 shard; đây chưa phải qualification P95/P99 hoặc evidence `QUERY_DB_READY`.
3. **`BT-09C — Outbox drain` (`FT-053 IMPLEMENTED — qualification pending`)**: FT-052 continuous refill chỉ đạt `5.387 records/s` ở 25k và 1M không hoàn tất. FT-053 thay per-event JPA lease bằng lane-level lease/fence, native JDBC projection và set-based mark; immediate-ack 1M đạt `8.264 ms`/`121.007 records/s`. Đây chưa là real-Kafka, representative payload, repeated-run, crash/reclaim hoặc production evidence.

4. **`BT-09D — Catalog bulk reconciliation` (`FT-057 READY`)**: [FT-055](./features/055-catalog-typed-ingest/03-plan.md) và [FT-056](./features/056-catalog-set-based-cte-merge/03-plan.md) chỉ còn là evidence lịch sử; V20–V22 đều thất bại và V22 làm 25K mất 39.278 s, 1M timeout. [FT-057](./features/057-catalog-bulk-reconciliation-data-plane/03-plan.md) thay data plane bằng append-only ingest, one-time reduction, coarse canonical merge/outbox và indexed sliding relay; combined gate 1M `<= 33.334 ms` (30K/s), stretch `<= 25.000 ms` (40K/s).
5. **`BT-09E — Query bulk projection`**: Batch consumer, staging/COPY hoặc set-based upsert, version guard, processed-event watermark.
6. **`BT-09F — Failure/operation evidence`**: DLT isolation/replay, crash/restart, duplicate, out-of-order, partial batch và reclaim.
7. **`BT-09G — Scale ladder`**: Chạy benchmark scale ladder 1K → 5K → 50K → 250K → 1M đo p50/p95/p99, lag, backlog, DB/WAL/IOPS/pool.

---

## Nền tảng hạ tầng và feature đã sẵn sàng cho BT-09

- [FT-034](./features/034-catalog-batch-existence-api/01-brief.md) & [FT-035](./features/035-scan-catalog-filtering/01-brief.md): Batch existence API và Scan-Catalog filtering (`IMPLEMENTED — verification deferred`).
- [FT-036](./features/036-event-contract-dlt-alignment/01-brief.md) & [FT-037](./features/037-outbox-backlog-capacity/01-brief.md): Event contract/DLT và Outbox backlog capacity (`IMPLEMENTED — verification deferred`).
- [FT-038](./features/038-targeted-issue-recheck/01-brief.md) & [FT-039](./features/039-durable-bulk-decision/01-brief.md): Targeted issue recheck và Durable bulk decision job (`IMPLEMENTED — verification deferred`).
- [FT-040](./features/040-primary-video-tag-ownership/01-brief.md), [FT-041](./features/041-scan-rerun-overwrite/01-brief.md), [FT-042](./features/042-primary-video-election/01-brief.md): Metadata repair, rerun overwrite và primary video election (`DONE`).
- [FT-043](./features/043-video-gallery-throughput/01-brief.md): Video Gallery & throughput event/outbox batch acknowledgement (`DONE — targeted Query integration verified`).
- [FT-046](./features/046-scan-core-pipeline-optimization/01-brief.md): Scan-core phase evidence, cold/warm benchmark matrix và SQL diff qualification (`DONE — benchmark waiver accepted`; không phải cross-service SLO).
- [FT-047](./features/047-scan-core-cold-path/01-brief.md): Cold root path bỏ materialize `scan_inventory_diff_stage`, vẫn giữ snapshot/retry safety (`DONE — benchmark waiver accepted`; không phải cross-service SLO).
- [FT-048](./features/048-scan-core-pipelined-reconciliation/01-brief.md): Bounded producer-consumer overlap giữa parse và commit (`DONE — COLD qualified`; chọn queue capacity 1, page/chunk 25k, giữ sequential fallback).
- [FT-049](./features/049-scan-core-scale-qualification/01-brief.md): Scale ladder và qualification cuối cho `scan-core` (`DEFERRED — intentionally skipped`; evidence 1M hiện tại của FT-046–048 vẫn giữ nguyên scope).
- Hot path persistence: [FT-028](./features/028-parallel-reconciliation-pipeline/03-plan.md) (parallel analyze, direct COPY), [FT-030](./features/030-scan-performance-telemetry/03-plan.md) (telemetry runtime), [FT-031](./features/031-scan-reconciliation-persistence-optimization/03-plan.md) (benchmark 1M file < 30s), [FT-032](./features/032-scan-review-queue/03-plan.md) & [FT-033](./features/033-scan-review-read-model/03-plan.md) (review queue/read model).

---

## Lộ trình sau khi hoàn tất SC-01 (Post SC-01 Roadmap & Deferred Gates)

Sau khi hoàn tất toàn bộ pipeline SC-01 BT-09 (từ BT-09A đến BT-09G), các giai đoạn tiếp theo của dự án theo [02-PLAN.md](./architecture/02-PLAN.md) gồm:

### 1. Verification & Hardening Gate (P0 / P1)
- **Hardening P0**: Security boundary, Nginx drive exposure (`TD-009`), Scan restart recovery (`TD-010`), Walker liveness/watchdog (`TD-011`), Durable job lease fencing (`TD-012`).
- **Runtime Verification**: Chạy Testcontainers cho semantics (`FT-025`), timeout/lease-loss (`FT-026`), E2E Gateway/SSE (`FT-027`), Review projection cutover (`FT-033`), DLT poison event & replay procedure (`TD-015`).

### 2. Giai đoạn tiếp theo (Phase Roadmap)
- **Phase 4 — Media Worker Processing Foundation** ([FT-013](./features/013-media-worker-processing-foundation/03-plan.md)):
  - Consume `processing.requested` qua Kafka consumer group.
  - Trích xuất technical metadata, sinh thumbnail, GIF preview và hash file (SHA-256).
  - Publish `processing.completed` và Catalog cập nhật asset completion.
- **Phase 7 — Importer & Backfill V1 → V2**:
  - Importer read-only từ V1, dry-run, batch idempotent, checkpoint và reconciliation đối soát.
  - Rebuild Query projection từ Catalog/event.
- **Phase 8 — Observability mở rộng & Production Hardening**:
  - Cấu hình Alert rules, SLO/error budget, k6 load test toàn diện, profiling sâu (JFR/JMH).
- **Phase 9 (Optional Labs)**:
  - Schema Registry (Avro/Protobuf), Kafka Streams, Kubernetes local (kind/k3d), GraalVM native image.

---

## Nợ kỹ thuật đang mở (Technical Debt Snapshot)

Xem chi tiết tại [TECHNICAL_DEBT.md](./TECHNICAL_DEBT.md).
- **P0**: `TD-009` (Security/network boundary) → `TD-010` (Scan restart recovery) → `TD-011` (Walker liveness) → `TD-012` (Job lease fencing).
- **P1**: `TD-013` (Outbox throughput) → `TD-014` (Gateway operations route) → `TD-015` (Query DLT observer) → `TD-016` (N+1 query/lock) → `TD-017` (Deep pagination cursor).
- **P2**: `TD-018` đến `TD-022` (Clean code, config provider, retention purge, split classes).

---

## Việc tiếp theo theo thứ tự ưu tiên (Action Plan)

1. **Triển khai FT-057 Catalog bulk reconciliation**: bỏ reduction upsert trong ingest slice, operation-wide rebuild trong page loop, claim/release mỗi page và relay hash-scan/wave barrier; qualification dùng một combined clock từ first Catalog receive tới final broker ack.
2. **Qualification còn mở của BT-09C:** [FT-053](./features/053-lane-fenced-outbox-data-plane/03-plan.md) đã vượt isolated immediate-ack floor nhưng vẫn cần real-Kafka, representative payload, repeated-run và crash/reclaim/broker-failure evidence; giữ `TD-013` active.
3. **Sau FT-057:** mở **`BT-09E`** (Query bulk) → **`BT-09F`** → **`BT-09G`**. Không bắt đầu BT-09E khi Catalog mới đúng semantics nhưng chưa đạt combined gate 1M tối thiểu 30K input records/s.
4. **Giai đoạn sau khi thông luồng SC-01:** thực hiện Hardening P0 (`TD-009` → `TD-012`), chạy Testcontainers / Flyway / DLT verification, chốt E2E Gateway/FE cutover.
5. **Giai đoạn phát triển tính năng mới:** triển khai **Phase 4 Media Worker** ([FT-013](./features/013-media-worker-processing-foundation/03-plan.md)) → **Phase 7 Importer V1** → **Phase 8 Observability mở rộng**.
