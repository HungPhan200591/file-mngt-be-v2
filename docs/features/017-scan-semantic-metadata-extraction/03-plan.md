# 017 Scan semantic metadata extraction — Plan

Status: DONE — implementation hoàn tất; Maven integration test chờ quyền chạy.
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`, `scan_db`, Scan OpenAPI v1.
- Scope: extractor/value object, persistence mapping evidence hiện có, proposal response và Scan integration test.
- Must preserve: read-only filesystem; no Catalog database/API/event change; no absolute path; no metadata guessing; existing proposal identity/decision behavior; source dưới 500 dòng/file.
- Read on demand: `apps/scan-service/CONTEXT.md`, feature `004`, Scan OpenAPI, `ScanService`, proposal entity/DTO và integration test.

## Bước triển khai

1. Tạo immutable parse result/evidence và extractor theo `ScanProfile`.
2. Serialize evidence vào cột `scan_proposal.evidence`; hydrate evidence khi list proposal.
3. Giữ proposal cũ tương thích với object evidence rỗng hoặc malformed JSON an toàn.
4. Thêm fixture path có directory và test các field common/profile-specific, bảo đảm không có absolute root.
5. Cập nhật Scan OpenAPI/feature docs nếu response implementation làm rõ contract hiện có.

## Kiểm tra

- Static: `git diff --check`, source cap, evidence không chứa root absolute.
- Khi được phép: `./mvnw test -pl apps/scan-service -am` bằng `corretto-25` và format source chạm bằng Spotless.

## Rollout và rollback

- Scan run mới có evidence đầy đủ; run cũ giữ `{}`.
- Rollback bằng revert code; không migration/backfill/xóa `scan_db`.

## Source-of-truth audit

- Không đổi ownership, database schema, Kafka event hoặc ADR. Chỉ hoàn tất field `evidence` đã có trong Scan OpenAPI.

## Implementation handoff — 2026-08-04

- Thêm `ScanMetadataExtractor`; proposal mới persist common/profile evidence và proposal API hydrate JSON evidence.
- Thêm regression fixture có nested path, xác nhận evidence profile `JOKE_*` có `bracketCode`, extension và không chứa absolute root.
- Static `git diff --check` đã chạy; chưa chạy Maven/Spotless hay service theo boundary hiện tại.
