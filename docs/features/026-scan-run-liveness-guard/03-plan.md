# 026 Scan run liveness guard — Plan

Status: IMPLEMENTED — VERIFY PENDING
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`.
- Scope/files: `ScanProperties`, `application.yml`, scheduler config, deadline guard/
  expiry handler, timeout adapter, `ScanRunRepository`, `ScanChunkCommitter`,
  `ScanExecutor`, `ScanService`, failure handler và `docs/STATUS.md`.
- Must preserve: 60-second lease fencing, root-level unique `RUNNING` constraint,
  bounded memory/COPY staging, exact inventory semantics, REST/Kafka contract và
  no absolute-path logs.
- Read on demand: FT-022 lease, FT-025 reconciliation, Spring `TaskScheduler`,
  PostgreSQL `SET LOCAL statement_timeout`.

## Bước triển khai

1. Thêm policy timeout scan có cấu hình, đặt cục bộ trong transaction-bound DB
   connection trước diff/COPY/mutation/finalize.
2. Tạo scheduler riêng và guard one-shot theo run; re-arm sau create/checkpoint,
   cancel sau terminal state.
3. Thêm conditional repository update và expiry handler; chỉ winner fail/cleanup.
4. Nối timeout/guard vào executor, committer, service và failure path; giữ terminal
   state không bị failure muộn ghi đè.
5. Cập nhật status snapshot, audit code theo architecture-quality-review. (Đã hoàn tất)

## Kiểm tra

- Không chạy test/build theo yêu cầu người dùng.
- Trước handoff: `git diff --check`, kiểm tra line cap, transaction boundary và
  source-of-truth/doc owner.

## Rollout và rollback

- Rollout: restart Scan Service để nạp bean/config mới; không cần Flyway.
- Rollback: revert code/config feature; không có persistent schema cần rollback.

## Tài liệu cần cập nhật

- `docs/STATUS.md` giữ feature active cho đến khi người dùng cho phép verification.
