# FT-054 — Operation Coalescing Runbook

## Điều kiện trước khi enable

- Flyway Catalog `V15`–`V18` và Scan `V27` đã pass trên empty DB lẫn DB có backlog.
- Focused contract/integration/fault test pass; canonical 1M và real-Kafka relay đạt gate trong Plan.
- Topic `media.file.discovered.v2`, `media.approval.watermark.v1` và `media.subject.changed.v2` đã có partition/retention đúng contract.
- Không còn unresolved Catalog DLT hoặc operation `BLOCKED` chưa reconcile.

## Mutual exclusion

Legacy và operation data plane không được cùng owner output outbox:

```text
CATALOG_OUTBOX_ENABLED=false
CATALOG_OUTBOX_OPERATION_RELAY_ENABLED=true
```

Enable operation ingest/finalizer sau khi schema và control consumer sẵn sàng:

```text
CATALOG_OPERATION_CONSUMER_ENABLED=true
CATALOG_OPERATION_FINALIZER_ENABLED=true
```

Startup fail-fast nếu legacy publisher và native relay cùng bật. Single decision `operationId=null` vẫn đi
legacy canonical handler; bulk operation đi durable staging/finalizer và không phát snapshot v1 trung gian.

## Quan sát canary

- Operation: `received_record_count = expected_discovery_record_count`, 64 lane hội tụ, không có fence mismatch.
- Canonical: `completed_subject_count`, `final_snapshot_count`, payload envelope và operation status.
- Relay: pending/oldest age, ack latency, failure, lease mismatch và pressure state.
- Failure: `catalog_dead_letter_event.resolution_state`, `failure_code`, operation `BLOCKED` và staging age.

Không log payload, identity, actress hoặc media path. Không dùng exact backlog count trong hot relay loop.

## Rollback

1. Tắt `CATALOG_OPERATION_CONSUMER_ENABLED` để dừng nhận bulk poll mới.
2. Tắt `CATALOG_OPERATION_FINALIZER_ENABLED` và native relay; chờ transaction hiện tại kết thúc hoặc statement timeout, sau đó chờ lane lease hết hạn.
3. Giữ nguyên staging, operation ledger, DLT và outbox để reconciliation; không xóa/reset offset tự động.
4. Chỉ sau khi native lane không còn owner hợp lệ, bật lại `CATALOG_OUTBOX_ENABLED=true`.
5. Reset/replay topic hoặc dữ liệu chỉ thực hiện theo quyền riêng và run manifest đã được duyệt.

## Reconcile trước replay

- Xác nhận failure code và source coordinate; resolve DLT trước khi cho operation hoàn tất.
- Replay giữ `operationId`; technical delivery có thể có `eventId` mới nhưng canonical effect vẫn bị chặn bởi workset/outbox uniqueness và subject version.
- Chỉ purge staging sau terminal reconciliation và retention window; purge không nằm trên critical path.
