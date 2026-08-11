# FT-033 — Kế hoạch Scan review read model

Status: DONE — implementation complete; build/Testcontainers/runtime verification deferred by user
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service` / `scan_db`; FE V2 chỉ dùng contract REST hiện có.
- Scope/files: Flyway projection schema; JDBC task/snapshot/query adapters; projector scheduler/transaction;
  terminal handoff; decision synchronization; query and bulk-action cutover; owner/status documentation.
- Must preserve: direct `COPY`, set-based inventory, chunk `REQUIRES_NEW`, Scan lease fence, approval outbox
  atomicity, immutable `APPROVE`, REST/Kafka/SSE compatibility và source dưới 500 dòng/file.
- Read on demand: FT-028/031/032, `apps/scan-service/CONTEXT.md`, coding rules, TD-006 và architecture review.

## Quyết định đã khóa trước code

1. Async root rebuild set-based; không dùng `UNLOGGED scan_inventory_diff_stage` làm replay source.
2. Database polling task nội bộ; không thêm outbox/Kafka contract.
3. Atomic generation swap, root fence và decision merge theo Design.
4. API fallback về historical query khi projection chưa READY; không đổi OpenAPI.
5. Worker có statement timeout, lease, retry/reaper, terminal `FAILED` và shutdown không release lease.

## Bước triển khai

1. Thêm migration root/task/proposal/issue projection, unique/index và bootstrap task cho root đã có run hoàn tất.
2. Thêm JDBC store cho enqueue, claim/reclaim, set-based rebuild, conditional swap, failure và cleanup generation.
3. Enqueue task O(1) trong transaction finalize; thêm scheduler và worker transaction tách bean để Spring proxy
   áp dụng transaction đúng.
4. Thêm query adapter đọc visible generation; chuyển queue/issues/summary sang projection có fallback.
5. Đồng bộ decision/reopen với visible projection dưới root lock; bulk selection dùng projection batch 500.
6. Thêm test unit/integration cho generation ordering, crash/retry, decision race, fallback và pagination.
7. Tự audit coding rules/architecture review; cập nhật context, status và evidence đã chạy.

## Kiểm tra

- Static: formatter Java, `git diff --check`, source cap, migration/entity alignment, contract/doc links.
- Unit: routing projection/fallback, task state, decision mapping và batch selection.
- Testcontainers: terminal atomic handoff, stale reclaim, old-generation no-op, decision/projector race, rebuild
  root, stable pagination và rollback khi statement timeout.
- Runtime: fixture 1M, query plan/buffer, projection lag/backlog và Scan hot-path không regress.
- Maven/Testcontainers/runtime chỉ chạy khi người dùng cho phép; dùng IntelliJ SDK `corretto-25`.

## Kết quả triển khai

- Đã thêm V14 với root watermark, durable task, proposal/issue generation snapshot và bootstrap task.
- Terminal finalize enqueue task trong cùng transaction; worker claim theo lease và không chạy đồng thời hai
  generation cùng root.
- Root rebuild dùng SQL set-based, merge decision dưới root lock và conditional generation swap; stale worker
  không thể công bố snapshot.
- Queue/issues/summary đọc projection khi READY, fallback toàn bộ về historical query khi chưa READY.
- Single/bulk approve/reject/reopen giữ write model + approval outbox + visible projection trong transaction;
  bulk projection xử lý từng transaction tối đa 500 candidate.
- Rollback flag: `scan.review-projection.enabled=false`.
- Đã chạy `spotless:apply` cho `scan-service`, IntelliJ inspection trên toàn bộ file chạm trả 0 Java error và
  `git diff --check` pass. Không chạy compile/test/Testcontainers/migration/runtime theo chỉ đạo người dùng.
- Implementation review: [06-implementation-review.md](./06-implementation-review.md).

## Rollout và rollback

- Additive migration bootstrap task; reader fallback cho tới khi scope READY rồi tự dùng projection.
- Rollback bằng `scan.review-projection.enabled=false`; worker có thể dừng mà write model/API cũ vẫn hoạt động.
- Không drop projection hoặc write model trong rollback. Chỉ dọn generation cũ sau swap thành công.

## Tài liệu cần cập nhật

- `apps/scan-service/CONTEXT.md`, `docs/STATUS.md` và Plan này khi code/evidence hoàn tất.
- Không đổi OpenAPI/event/ADR: path/shape, service/database owner và integration boundary không đổi.
- TD-006 không thuộc phạm vi và vẫn mở.
