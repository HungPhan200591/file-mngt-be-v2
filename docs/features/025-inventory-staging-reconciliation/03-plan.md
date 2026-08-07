# FT-025 — Inventory staging reconciliation — Plan

Status: IMPLEMENTED — VERIFY PENDING
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`.
- Scope/files: migration V9; inventory entity/repository/writers; `ScanChunk`, matcher, executor, committer/service; scan tests; SC-01 deep-dive/question chain.
- Must preserve: bounded chunk memory, lease fencing, exact `MISSING`, profile filtering, timestamp precision fix, no absolute-path logs, no REST/Kafka change và lịch sử FT-024.
- Read on demand: [FT-024 baseline](../024-inventory-matcher/03-plan.md), `docs/architecture/03-CODING_RULES.md`, pgJDBC `CopyManager`, PostgreSQL `UNLOGGED` semantics.

## Bước triển khai

1. Thêm V9 tạo `scan_inventory_stage`, index anti-join và drop `last_seen_run_id`/index cũ.
2. Thêm adapter staging COPY/cleanup; đổi inventory writer thành changed-only upsert và mark missing bằng anti-join.
3. Bổ sung state vào snapshot/matcher; tách seen items với changed items trong chunk.
4. Giữ staging, inventory mutation, proposal/issue và checkpoint trong chunk transaction; finalize lease-fenced dọn staging.
5. Dọn staging sau failure và trước run mới khi stale run đã được đóng.
6. Cập nhật integration/unit test source cho unchanged, changed, revived, missing và cleanup.
7. Thêm phần “Update FT-025” vào SC-01 deep-dive/question chain; không sửa claim lịch sử FT-024 thành như chưa từng tồn tại.
8. Sau benchmark runtime, tăng reconciliation chunk lên 10.000 và giảm progress
   log để xử lý transaction amplification còn lại; giữ nguyên transaction/lease
   invariant của FT-025.

## Kết quả triển khai

- Đã thêm V9, staging COPY adapter, changed-only upsert, anti-join MISSING và cleanup theo lifecycle run.
- Đã xóa `last_seen_run_id` khỏi entity/code hiện hành; V8 và FT-024 giữ nguyên làm lịch sử migration/feature.
- Đã cập nhật test source cho unchanged inventory không đổi `updated_at`, staging cleanup, timestamp precision và MISSING tái xuất hiện.
- Đã xử lý follow-up FT-025.1: một triệu file giảm từ 2.000 xuống tối đa 100
  reconciliation chunk transaction; chưa benchmark lại theo yêu cầu không chạy.
- Filesystem-only benchmark trước follow-up đã đo 17,832 giây; chưa chạy
  test/build/migration hoặc post-fix scan benchmark theo rule người dùng, vì vậy
  feature chưa chuyển `DONE`.

## Kiểm tra

- Không chạy test/build/post-fix benchmark theo rule người dùng hiện tại.
- Đã chạy formatting-only `spotless:apply` bằng JDK `corretto-25`; không compile/test.
- Đã kiểm tra tĩnh: `git diff HEAD --check`, không còn Java usage `last_seen_run_id`, source đúng line cap, migration/entity alignment và contract/ownership audit.
- Khi người dùng cho phép: chạy test module Scan bằng JDK `corretto-25`, sau đó benchmark cold/warm scan sau FT-025.1.

## Rollout và rollback

- Rollout yêu cầu restart Scan Service để Flyway chạy V9; Agent không tự chạy migration/service.
- Flyway append-only không rollback V9 tại runtime. Nếu cần quay lại code cũ, phải roll-forward migration mới khôi phục cột trước khi deploy FT-024; không dùng repository reset.
- Staging là scratch; có thể truncate/delete theo run khi vận hành, nhưng không tác động inventory canonical.

## Tài liệu cần cập nhật

- `docs/STATUS.md`: feature active/verification pending.
- SC-01 `03-cross-service-deduplication.md` và `question-bank/01-question-chain.md`: append update FT-025.
- Không cần đổi OpenAPI, event contract hoặc ADR vì public boundary/ownership không đổi.
