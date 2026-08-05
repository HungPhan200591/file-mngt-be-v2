# `media.subject.changed.v1`

## Ownership

- Producer: `catalog-service` transactional outbox.
- Consumer dự kiến: `query-service`; consumer khác phải đăng ký theo feature riêng.
- Topic: `media.subject.changed.v1`.
- Partition key: `subjectId` dạng UUID.

## Payload v1

```json
{
  "eventId": "d4e3ceef-72a4-4f62-a84b-c48be6e4aa45",
  "eventType": "media.subject.changed.v1",
  "occurredAt": "2026-08-01T14:00:00Z",
  "subjectId": "97dd477a-9304-4e91-84d0-96aa85ead134",
  "subjectVersion": 2,
  "region": "JOKE",
  "subjectType": "VIDEO",
  "identityKey": "START-001",
  "displayTitle": "Actress - [START-001]",
  "createdAt": "2026-08-01T13:00:00Z",
  "assets": [
    {
      "assetId": "0371fd34-6aac-47ce-94ad-c8c58966af86",
      "role": "PRIMARY_VIDEO",
      "relativePath": "Actress - [START-001].mp4"
    }
  ]
}
```

`assets` là full snapshot tại `subjectVersion`, không phải delta. `displayTitle` có thể là `null`; `assets` có thể rỗng. Payload không chứa absolute filesystem path.

## Semantics và idempotency

- Một canonical mutation tạo đúng một `eventId`; retry publisher giữ nguyên `eventId` và payload.
- Consumer dedupe theo `eventId`. Với cùng `subjectId`, consumer chỉ áp dụng event có `subjectVersion` lớn hơn version projection hiện tại; version bằng hoặc thấp hơn là no-op.
- Consumer upsert subject và thay thế asset projection bằng full `assets` snapshot trong cùng transaction của read model.
- Duplicate/no-op input ở Catalog không tạo event mới. Event không có `changeType`; việc thiếu asset trong snapshot chỉ mang nghĩa trạng thái hiện tại, chưa phải contract xóa canonical asset.

## Delivery và compatibility

- Delivery at-least-once; thứ tự được giữ theo partition key `subjectId` trong một topic partition, nhưng consumer vẫn phải kiểm tra version.
- Catalog chỉ đánh dấu outbox published sau Kafka acknowledgement. Kafka lỗi không rollback canonical transaction; pending event được retry.
- `X-Correlation-Id` và W3C `traceparent` là Kafka headers, không phải field JSON. Catalog lưu metadata này cùng outbox transaction để relay khôi phục context trước khi publish; Query chấp nhận record cũ không có header.
- Thêm field optional là backward-compatible. Không đổi nghĩa/xóa/đổi kiểu field v1; thay đổi breaking tạo event version mới.
- DLT của consumer dùng `<source-topic>.DLT` và giữ record gốc cùng Kafka error headers. Chính sách retry/replay thuộc consumer feature, không nằm trong payload v1.
