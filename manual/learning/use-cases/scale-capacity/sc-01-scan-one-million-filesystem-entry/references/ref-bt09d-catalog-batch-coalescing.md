# Reference Capsule: BT-09D — Catalog Logical Shard Completion

> Implementation owner: [FT-059](../../../../../../docs/features/059-catalog-logical-shard-completion/03-plan.md)
> `READY — architecture/contract approved; implementation pending`.
> FT-054–FT-058 là historical/failed evidence; không route implementation về 16-unit shape.

## Failure boundary hiện hành

- FT-058 functional regression đạt `36/36`; combined 25K hoàn tất trong `4,935s`.
- Combined 1M vượt deadline 120 giây khi units `0–3` lặp `QueryTimeoutException`.
- Một unit khoảng 6.250 subjects trong một transaction; timeout rollback toàn unit nên checkpoint không tiến.
- Global completion barrier buộc Catalog đợi đủ operation trước reconciliation, giảm overlap với ingest/Query.
- Không tăng timeout, retry, worker hoặc tạo SQL candidate mới cùng 16-unit transaction shape.

## Kiến trúc FT-059

```text
Scan subject-key shard + exact transactional completion marker
→ Kafka discovery + media.approval.shard.completed.v1
→ Catalog typed ingest/dedupe + per-shard counter
→ marker + exact equality + shard DLT gate → seal shard
→ stable shard workset + durable bounded pages
→ canonical + final snapshot outbox + page checkpoint atomic
→ indexed sliding relay → Query consume sớm
→ all shards + exact sums + final broker ACK → CATALOG_COMMITTED
```

Logical shard dùng canonical `region:subjectType:identityKey`, không dùng Kafka partition vật lý hoặc proposal
UUID. Contract version `SUBJECT_KEY_MD5_12_RANGE_V1` tạo 12-bit routing bucket; candidate 64 shards nhưng Scan/
Catalog worker concurrency vẫn bounded riêng. Marker và data khác topic có thể đến lệch thứ tự; equality gate
mới là authority.

## Invariants

1. Mọi discovery của cùng subject/operation nằm cùng shard; routing version/count immutable theo operation.
2. Dedupe `eventId` trước shard counter; marker duplicate cùng payload là no-op, conflict phải block.
3. Shard chỉ seal khi marker, exact unique count và DLT gate hội tụ; unique late input sau seal phải block.
4. Winner vẫn theo `(sourcePartition, sourceOffset, eventId)`; giữ primary/tags/tombstone/version/size semantics.
5. Canonical mutation, một final `media.subject.changed.v2`, outbox và page checkpoint commit atomic.
6. Retry chỉ chạy page chưa commit; lease/fence ngăn stale worker checkpoint hoặc publish effect mới.
7. Global `CATALOG_COMMITTED` chờ mọi shard, exact counts, zero unresolved DLT và final broker ACK durable mark.
8. PostgreSQL vẫn là Catalog source of truth; không cross-database write/join hoặc Java whole-operation reducer.

## Gate và evidence boundary

- Profile chuẩn: 1M discovery input, 100K subjects, fan-out 10 assets/subject.
- Clock: first Catalog receive → final output broker ACK; seed/assignment/warm-up ngoài clock.
- Implementation gate: ba measured run 1M liên tiếp `<= 120s`, exact cardinality và resource bounded.
- `30K–40K input records/s` chỉ là stretch; output rate báo riêng.
- Candidate 64 shards/page 250–500 là calibration hypothesis, không phải measured capacity.
- Local/Testcontainers qualification không phải production P95/P99 và không qualify `QUERY_DB_READY`.

## Route đọc tiếp

- Contract/architecture/rollback: [FT-059 Plan](../../../../../../docs/features/059-catalog-logical-shard-completion/03-plan.md).
- Event payload/routing/idempotency: [shard completion contract](../../../../../../docs/contracts/events/media.approval.shard.completed.v1.md).
- Long-term decision: [ADR-006](../../../../../../docs/adr/ADR-006-logical-completion-shards.md).
- Failure evidence: [FT-058 report](../../../../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/05-ft058-reliability-hardening.md).
