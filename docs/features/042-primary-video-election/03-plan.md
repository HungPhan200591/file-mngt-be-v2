# FT-042 — Primary video election — Plan

Status: DONE — static verification only

## Execution capsule

- Owner: Scan producer, Catalog canonical model; Query/FE contract giữ nguyên.
- Scope: event contract, Scan candidate role, Catalog asset tags/election/removal, Flyway và tests.
- Must preserve: event idempotency, locator uniqueness, một primary tối đa, transactional outbox.
- Read on demand: `media.file.discovered.v2`, FT-040 và context Scan/Catalog.

## Bước triển khai

1. Làm rõ event semantics và chuyển Scan video role sang `VIDEO`.
2. Thêm asset tag persistence và domain election trong Catalog.
3. Áp dụng election khi discovery và khi xóa primary.
4. Cập nhật existence classification để `VIDEO` mới không conflict với primary hiện hữu.
5. Thêm regression tests cho hai thứ tự event và re-election khi xóa.
6. Audit source of truth, format file Java đã chạm và chạy kiểm tra được cho phép.

## Verify

- Static inspection, line cap và `git diff --check`: hoàn tất.
- Build/test/runtime: chưa chạy vì người dùng chưa yêu cầu rõ.

## Rollback

Revert producer/election; bảng asset tag additive có thể giữ lại. Không rollback migration đã apply.
