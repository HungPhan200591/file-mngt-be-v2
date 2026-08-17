# FT-045 — Plan: Durable Scan Approval & Decision Chunking

Status: `READY`
Owner: `scan-service`
Must preserve: transactional outbox atomicity, operation cardinality, lease fencing, `scan_db` ownership.

## 1. Execution capsule

- **Owner**: `apps/scan-service/`; shared payload type tại `platform/event-contracts/`.
- **Scope**:
  - Migration `V22__add_scan_approval_operation.sql`.
  - Approval operation persistence/service/worker/status DTO.
  - `ScanRunDecisionBatch` orchestration và `ScanDecisionChunkExecutor` transaction boundary.
  - JDBC keyset read + decision/outbox/projection/checkpoint write.
  - Scan controller endpoints đã chốt trong OpenAPI.
  - `MediaFileDiscoveredV2`/`MediaFileRemovedV1` operation metadata và callsites.
  - Unit + PostgreSQL Testcontainers integration tests.
- **Read on demand**:
  - [Brief](./01-brief.md), [Design](./02-design.md).
  - [FT-044 handoff](../044-approve-1m-operation-contract/03-plan.md).
  - [BT-09B capsule](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/references/ref-bt09b-scan-decision-chunking.md).

## 2. Implementation steps

1. Tạo schema operation, active/claim index và operation metadata trên decision/outbox.
2. Tạo approval service: lock run, validate `COMPLETED`, count pending, commit `ACCEPTED`, status query.
3. Tạo claim/retry state service và scheduled worker với operation lease/deadline riêng.
4. Refactor `ScanRunDecisionBatch` thành non-transactional orchestrator; chunk executor là bean riêng
   `REQUIRES_NEW`.
5. Tạo JDBC repository khớp schema thật; batch insert decision/outbox, set-based projection update và
   conditional checkpoint/finalization.
6. Bổ sung `operationId`/`batchId` vào event contract/factory/outbox entity; giữ nullable cho single decision.
7. Wire API `POST approve`/`GET status`; loại bỏ run-wide endpoint đồng bộ cũ không còn trong OpenAPI.
8. Guard single decision/reopen trước active approval operation.

## 3. Verification matrix

- Unit: accept/status mapping, duplicate active operation, worker retry/fail, batch orchestration.
- Integration: `0`, `1`, full, exact-multiple, partial; atomic rollback decision/outbox/checkpoint;
  reclaim từ cursor; operation metadata; opposite/concurrent conflict; cardinality gate.
- Contract: producer payload deserialize ở Catalog; partition key và event type không đổi.
- Static: Palantir format, file <500 dòng, `git diff --check`, link/schema/source-of-truth audit.

Không coi unit mock là bằng chứng transaction. Runtime benchmark/P95/P99 và continuous relay thuộc
BT-09C/BT-09G, không phải completion gate của FT-045.

## 4. Rollback

- Revert API/worker/payload producer cùng nhau; không để operation active chạy qua hai runtime version.
- Migration append-only: nếu cần rollback sau khi đã áp dụng, tạo migration mới để vô hiệu hóa/drop artifact;
  không sửa `V22`.
- Local study topic/data có thể reset theo compatibility decision FT-044; không dual-publish.
