# `media.subject.deleted.v1`

- Producer: Catalog Service, khi asset cuối cùng của subject đã bị xóa.
- Consumer: Query Service.
- Partition key: `subjectId`.
- Idempotency key: `eventId`; ordering/fencing theo `subjectVersion`.
- Delivery: at-least-once.

## Payload

`eventId`, `eventType`, `occurredAt`, `subjectId`, `subjectVersion`.

Query chỉ áp dụng tombstone mới hơn projection hiện tại; duplicate hoặc tombstone stale là no-op. PostgreSQL projection và search-delete outbox được ghi trong cùng transaction; Redis bị evict sau commit.
