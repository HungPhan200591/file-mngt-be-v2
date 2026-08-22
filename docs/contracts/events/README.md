# Kafka event contracts

Mỗi event dùng một file `<event-type>.md`: producer, consumer, topic, partition key, JSON schema, version, retry, DLT và idempotency.

Event mới hoặc event thay đổi phải được chốt trong Feature Design trước khi code producer/consumer.

## Contract runtime và approved target cho SC-01

- [`media.file.discovered.v2`](./media.file.discovered.v2.md): Scan → Catalog data event.
- [`media.approval.shard.completed.v1`](./media.approval.shard.completed.v1.md): Scan → Catalog logical
  completion shard marker; FT-059 implementation đã có targeted verification, scale qualification còn mở.
- [`media.subject.changed.v2`](./media.subject.changed.v2.md): Catalog → Query final subject snapshot.
- [`media.approval.watermark.v1`](./media.approval.watermark.v1.md): completion manifest cardinality thấp.

`media.subject.changed.v1` đã retired; study project thay thẳng runtime bằng v2 ở BT-09D/BT-09E, không
dual-publish hoặc giữ backward compatibility.
