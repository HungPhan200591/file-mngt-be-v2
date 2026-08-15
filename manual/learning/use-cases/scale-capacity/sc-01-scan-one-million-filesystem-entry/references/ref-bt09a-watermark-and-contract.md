# BT-09A capsule — Operation contract & watermark

> Context ngắn để triển khai các lát BT-09B–E. Contract chi tiết là
> [`media.approval.watermark.v1`](../../../../../../docs/contracts/events/media.approval.watermark.v1.md).

## Lifecycle đã chốt

```text
ACCEPTED → APPROVAL_COMMITTED → CATALOG_COMMITTED → QUERY_DB_READY → SEARCH_READY
                      ↘ BLOCKED / FAILED / CANCELLED
```

- `ACCEPTED`: Scan commit operation row O(1), trả `202`; bắt đầu SLO.
- `APPROVAL_COMMITTED`: đủ 1M decision + discovery outbox đã commit theo chunk.
- `CATALOG_COMMITTED`: đủ unique input, coalesce xong, chốt `expectedSubjectCount`.
- `QUERY_DB_READY`: Query projected count khớp expected count, DLT bằng 0; kết thúc SLO.
- `SEARCH_READY`: async Elasticsearch, không chặn Query DB.

## Rule bảo vệ SLO

- Counter flush theo batch; không progress write per-record.
- Catalog phát một final `media.subject.changed.v2` snapshot cho mỗi subject/operation.
- Completion dùng equality gate theo expected cardinality, không giả định global Kafka ordering.
- Cache generation switch O(1); Redis lỗi thì bypass/fallback Query DB.
- Control topic chỉ O(stage) event cho mỗi operation.

## Version decision

- `media.subject.changed.v2` là runtime target duy nhất.
- Không dual-publish/backward compatibility; BT-09D/E thay thẳng v1 và reset local topic/projection.
- Unresolved DLT chuyển operation `BLOCKED`, cấm phát ready watermark.
