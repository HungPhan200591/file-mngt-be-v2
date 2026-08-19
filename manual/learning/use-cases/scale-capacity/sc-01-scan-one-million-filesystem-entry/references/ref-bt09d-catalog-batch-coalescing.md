# Reference Capsule: BT-09D — Catalog Operation Coalescing

> Implementation owner: [FT-054](../../../../../../docs/features/054-catalog-operation-coalescing/03-plan.md).
> Phạm vi: `media.file.discovered.v2` → canonical Catalog → `media.subject.changed.v2` + `CATALOG_COMMITTED`.

## Vấn đề

Catalog hiện xử lý từng event bằng một listener/transaction JPA, load và flush aggregate rồi tạo snapshot v1.
Với 1M input, cost transaction/version/outbox gần `O(events)`; cùng subject qua nhiều poll vẫn bị ghi nhiều lần.
Catalog outbox hiện còn fixed delay giữa các claim nên giảm event count nhưng chưa đủ bảo đảm relay budget.

Coalesce trong RAM của một Kafka poll là solution nửa vời vì poll không phải operation boundary. Manifest có
thể đến trước/sau data và một subject có thể trải qua nhiều poll/batchId.

## Target one-shot của FT-054

```text
Scan transactional APPROVAL_COMMITTED watermark
→ Catalog batch listener, bounded theo records + bytes
→ temp COPY + durable stage ON CONFLICT(eventId) DO NOTHING
→ operation ledger + exact received/expected equality gate
→ freeze unique subject workset
→ 64 logical lane native canonical merge
→ one subjectVersion + one final snapshot v2 mỗi changed subject
→ native continuous Catalog outbox relay
→ CATALOG_COMMITTED khi input/subject/outbox/DLT count đều exact
```

## Invariants

1. Staging là logged/durable; không giữ 1M event trong Java heap.
2. Dedupe input theo `eventId`; unique output theo `(operationId, subjectId)`.
3. Subject reducer deterministic theo Kafka source order; giữ primary election, tags và tombstone semantics.
4. Canonical write, final outbox và lane checkpoint atomic trong `catalog_db`.
5. Manifest/data reorder hội tụ bằng equality gate; unresolved DLT cấm `CATALOG_COMMITTED`.
6. Output relay dùng lane lease/fence + native fetch/mark + bounded async send; fixed-delay publisher chỉ rollback.
7. Query implementation không thuộc FT-054; output contract duy nhất là `media.subject.changed.v2`.

## Gate không được đẩy sang feature khác

- 1M representative input, 100k subject × 10 asset: canonical phase `<= 10s`, `>= 100k records/s`.
- Broker-ack + durable mark toàn bộ final snapshot: `<= 2s` trên qualification profile.
- Data loss, duplicate canonical effect, unresolved DLT: `0`.
- Nếu chưa đạt, tiếp tục profile/tối ưu SQL, index, lane, chunk và producer window trong FT-054;
  không tạo FT mới chỉ để hoàn tất throughput BT-09D.

## Evidence boundary

Stripe fast-path/slow-path và Uber Kafka DLT/idempotency hỗ trợ pattern ledger/replay/failure isolation,
nhưng không chứng minh throughput. Chỉ benchmark project với payload/schema/hardware/Kafka thật đóng gate.
