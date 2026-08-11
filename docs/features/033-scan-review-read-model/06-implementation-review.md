# FT-033 — Implementation architecture review

Ngày review: 2026-08-11
Scope: code và migration FT-033 sau khi xử lý các finding trong `05-architecture-review.md`.

## Kết luận

**Verdict: CONDITIONAL** — code flow đã hoàn tất; automated database/runtime evidence được người dùng cho
phép deferred để ưu tiên thông luồng.

| Tiêu chí | Trạng thái | Evidence |
| --- | --- | --- |
| Atomic terminal handoff | PASS | `ScanRunProgressWriter.complete` enqueue task cùng finalize transaction. |
| Durable rebuild source | PASS | Root rebuild từ write model durable; không dùng UNLOGGED staging. |
| Root ordering/fence | PASS | Generation tăng đơn điệu, root-level claim exclusion và conditional swap. |
| Decision race | PASS | Decision/projector dùng cùng root row lock; projector refresh decision trước swap. |
| Worker liveness | PASS về design/code | Lease, stale reclaim, retry budget, total deadline và terminal FAILED. |
| Graceful shutdown | PASS | Worker dừng claim mới; không release lease trong shutdown callback. |
| Projection freshness | PASS | Root/global fallback về historical authority cho tới READY. |
| Bounded bulk action | PASS | Candidate selection và transaction batch tối đa 500. |
| Resource isolation | PARTIAL | Một task/worker, một generation/root, statement timeout; chưa có datasource pool riêng. |
| Runtime/database evidence | MISSING | Compile/Testcontainers/migration/benchmark chưa chạy theo chỉ đạo người dùng. |

## Safety và liveness

- Worker cũ chỉ complete/swap khi task còn đúng `lease_owner` và `lease_until > CURRENT_TIMESTAMP`.
- Generation cũ không overwrite generation mới; task superseded hoàn tất no-op và tự dọn snapshot của nó.
- Worker crash rollback build transaction; task RUNNING hết lease được reclaim. Task quá retry/deadline chuyển
  FAILED và root tiếp tục fallback query authority.
- Shutdown không nhận task mới; lease chỉ được reclaim sau deadline nên không tạo hai owner hợp lệ.

## Architecture và contract

- Tất cả bảng và transaction thuộc `scan-service` / `scan_db`; không cross-database access.
- REST, Kafka, SSE và Gateway contract không đổi. Approval vẫn ghi decision + outbox atomic.
- Projection là derived state có thể rebuild; PostgreSQL write model vẫn là source of truth.
- Không cần ADR mới vì ownership, service boundary và technology không đổi.

## Residual evidence gap

1. Chạy compile/unit test và Testcontainers cho migration, lease expiry, old-generation no-op và decision race.
2. Benchmark 1M dưới projector load; đo projection lag, DB contention và query buffers.
3. Chỉ cân nhắc datasource pool riêng nếu benchmark chứng minh projector tranh connection/I/O với Scan.

Accepted risks/waivers: chỉ deferred verification theo yêu cầu hiện tại; không waiver correctness failure.
