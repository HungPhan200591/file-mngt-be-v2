# FT-044 — Plan: SC-01 BT-09A Operation Contract & Watermark

Status: `DONE — contract approved; runtime implementation pending BT-09B–BT-09E`  
Owner: `scan-service`, `catalog-service`, `query-service`, `gateway-service`  
Use case: [SC-01 Approve 1M Context](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md) — [BT-09A](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned)

## Execution capsule

- **Owner files**:
  - `docs/contracts/events/media.approval.watermark.v1.md`.
  - `docs/contracts/events/media.subject.changed.v2.md`.
  - `docs/contracts/openapi/scan-v1.yaml`.
  - `docs/features/044-approve-1m-operation-contract/`.
- **Must preserve**:
  - Service-owned database, transactional outbox, at-least-once + idempotency.
  - SLO start tại `ACCEPTED`, end tại Query commit `QUERY_DB_READY`.
  - O(stage/batch) completion tracking; không progress write per-record.
  - Redis/search không trở thành canonical dependency.
- **Read on demand**:
  - [BT-09A capsule](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/references/ref-bt09a-watermark-and-contract.md).

## Quyết định đã chốt

1. `ACCEPTED` tách khỏi `APPROVAL_COMMITTED`; HTTP `202` trả sau operation row commit O(1).
2. Completion dùng `media.approval.watermark.v1` và equality gate theo expected cardinality.
3. Catalog phát một final `media.subject.changed.v2` snapshot cho mỗi subject trong operation.
4. Query dùng bulk/set-based projection, version guard `>` và chỉ ready khi unresolved DLT bằng 0.
5. Cache generation switch O(1); Search là async lane.
6. V2 thay thẳng v1 trong study environment; không compatibility/dual-publish, cho phép reset local data/topic.

## Handoff sang các lát triển khai

| Lát | Contract phải hiện thực |
| --- | --- |
| BT-09B | Operation row `ACCEPTED`; bounded decision/outbox; `operationId`/`batchId` durable payload. |
| BT-09C | Continuous drain, bounded in-flight và `APPROVAL_COMMITTED` manifest. |
| BT-09D | Operation-scoped dedupe/coalesce; one final subject snapshot; `CATALOG_COMMITTED`. |
| BT-09E | Bulk v2 consumer/upsert; completion counter; cache generation; `QUERY_DB_READY`. |
| BT-09F | `BLOCKED`/DLT/replay/restart/duplicate/out-of-order evidence. |
| BT-09G | Scale ladder và P95/P99 qualification theo SLO contract. |

## Verification BT-09A

- Contract owner, event version, lifecycle, completion cardinality, DLT và cache semantics đã chốt.
- Relative links và stale runtime target v1 được audit trong phạm vi owner hiện hành.
- Không chạy build/test/service vì BT-09A chỉ thay contract/docs; runtime vẫn được triển khai ở lát sau.

## Rollback

Trước khi BT-09B–E có code, rollback chỉ là revert bộ contract/feature docs. Sau khi đã thay runtime v2,
study project rollback bằng reset local topic/projection và revert cùng producer + consumer; không giữ dual stack.
