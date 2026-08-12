# FT-043 — Video Gallery và throughput event — Plan

Status: IMPLEMENTED — static verification only

## Execution capsule

- Owner: Scan outbox, Catalog aggregate/event, Query projection/API, FE Gallery.
- Scope: batch publish, subject version, asset tags, video page contract, Gallery card/detail.
- Must preserve: transactional outbox, eventId dedupe, Kafka ordering theo key, subject API compatibility.
- Read on demand: FT-042, Query OpenAPI, Gallery context.

## Các bước

1. Sửa aggregate version và bổ sung asset tags vào Catalog snapshot.
2. Project asset tags trong Query, thêm video repository/service/controller và OpenAPI.
3. Chuyển Scan/Catalog outbox adapter sang batch asynchronous acknowledgement.
4. Chuyển FE Gallery sang video page; detail vẫn query theo subject.
5. Audit source of truth, line cap, format và `git diff --check`.

## Verify

- Static contract/diff/line-cap audit, architecture review, formatter và `git diff --check`: hoàn tất.
- Build/test/runtime/migration chỉ chạy khi người dùng cho phép.

## Rollback

FE có thể quay lại subject endpoint. Video endpoint và asset-tag columns là additive. Publisher có thể quay lại per-record
acknowledgement mà không đổi outbox schema.
