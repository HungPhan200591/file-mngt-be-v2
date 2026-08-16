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
9. FT-025.2 thay lookup/COPY chung chunk bằng hai phase: discovery dùng
   `walkFileTree` và streaming COPY segment 500.000 row; reconciliation dùng SQL
   set-based trả riêng file new/changed/revived để Java parse theo business chunk.
10. Mỗi discovery segment commit staging, progress/checkpoint và lease cùng
    transaction; không materialize 500.000 item trong heap và không dùng câu
    `IN` 500.000 parameter.
11. FT-025.3 sửa runtime hang ở reconciliation: refresh statistics staging sau
    discovery và dùng correlated composite-key lookup để inventory index sử dụng
    cả `root_key` lẫn `source_relative_path`.
12. FT-025.4 materialize tập changed đúng một lần vào `scan_inventory_diff_stage`;
    progress lấy row count từ chính `INSERT ... SELECT`, còn keyset reader chỉ duyệt
    tập diff nhỏ thay vì quét lại toàn bộ staging theo từng page.

## Kết quả triển khai

- Đã thêm V9, staging COPY adapter, changed-only upsert, anti-join MISSING và cleanup theo lifecycle run.
- Đã xóa `last_seen_run_id` khỏi entity/code hiện hành; V8 và FT-024 giữ nguyên làm lịch sử migration/feature.
- Đã cập nhật test source cho unchanged inventory không đổi `updated_at`, staging cleanup, timestamp precision và MISSING tái xuất hiện.
- Đã xử lý follow-up FT-025.1: một triệu file giảm từ 2.000 xuống tối đa 100
  reconciliation chunk transaction; chưa benchmark lại theo yêu cầu không chạy.
- Đã implement FT-025.2: `walkFileTree` producer queue 1.024 item, streaming COPY
  segment 500.000, set-based changed keyset reader và conditional lease fence
  trước commit discovery. Production path không còn lookup `IN` theo seen chunk.
- Changed inventory/proposal/issue commit theo business chunk cấu hình được, mặc định 15.000;
  warm scan zero-change không materialize inventory snapshot hoặc changed item.
- Đã sửa FT-025.3 từ runtime evidence run `cb6ed18e...`: discovery hoàn tất
  27.122 file nhưng LEFT JOIN diff chạy hơn 2 phút vì planner chỉ dùng
  `root_key` của inventory index rồi join-filter từng path. Reconciliation hiện
  refresh staging statistics, keyset staging theo page 25.000, dùng correlated
  composite-key lookup và heartbeat lease cho page zero-change.
- Filesystem-only benchmark trước follow-up đã đo 17,832 giây; chưa chạy
  test/build/migration hoặc post-FT-025.3 scan benchmark theo rule người dùng, vì vậy
  feature chưa chuyển `DONE`.
- Runtime 1.000.000 file lúc 23:51 ngày 07/08/2026 hoàn tất 1m55s; evidence từ UI cho
  thấy discovery/COPY nhanh nhưng reconciliation lâu. FT-025.4 loại bỏ `countChanged`
  full pass và các full staging page lặp lại; business chunk mặc định tăng lên 15.000.
  Chưa chạy migration/build/test hay benchmark sau thay đổi.

## Kiểm tra

- Không chạy test/build/post-FT-025.3 benchmark theo rule người dùng hiện tại.
- Đã chạy formatting-only `spotless:apply` bằng JDK `corretto-25`; không compile/test.
- Đã kiểm tra tĩnh: `git diff HEAD --check`, không còn Java usage `last_seen_run_id`, source đúng line cap, migration/entity alignment và contract/ownership audit.
- Khi người dùng cho phép: chạy test module Scan bằng JDK `corretto-25`, sau đó benchmark cold/warm scan sau FT-025.3.

## Rollout và rollback

- Rollout yêu cầu restart Scan Service để Flyway chạy V9; Agent không tự chạy migration/service.
- Flyway append-only không rollback V9 tại runtime. Nếu cần quay lại code cũ, phải roll-forward migration mới khôi phục cột trước khi deploy FT-024; không dùng repository reset.
- Staging là scratch; có thể truncate/delete theo run khi vận hành, nhưng không tác động inventory canonical.

## Tài liệu cần cập nhật

- `docs/STATUS.md`: feature active/verification pending.
- SC-01 `03-cross-service-deduplication.md` và `question-bank/01-question-chain.md`: append update FT-025.
- Không cần đổi OpenAPI, event contract hoặc ADR vì public boundary/ownership không đổi.
