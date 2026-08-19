# `media.subject.changed.v2`

Status: **ACTIVE — runtime target sau BT-09D/BT-09E**

## Ownership

- Producer: `catalog-service` transactional outbox.
- Consumer: `query-service` batch projection.
- Topic/event type: `media.subject.changed.v2`.
- Partition key: `subjectId`.
- Delivery: at-least-once; DLT: `media.subject.changed.v2.DLT`.

Đây là contract duy nhất của SC-01 sau khi BT-09D/BT-09E triển khai. Study project không dual-publish,
không duy trì consumer v1 và được phép reset topic/database local trước qualification.

## Payload

```json
{
  "eventId": "019ffb4f-1111-7aaa-8bbb-111111111111",
  "eventType": "media.subject.changed.v2",
  "occurredAt": "2026-08-15T22:00:18Z",
  "operationId": "019ffb4f-2222-7aaa-8bbb-222222222222",
  "batchId": "catalog-output-00042",
  "subjectId": "019ffb4f-3333-7aaa-8bbb-333333333333",
  "subjectVersion": 14,
  "region": "JOKE",
  "subjectType": "VIDEO",
  "identityKey": "START-001",
  "displayTitle": "Actress - [START-001]",
  "baseCode": "START",
  "part": "001",
  "studioCode": "STUDIO",
  "actressNames": ["Actress"],
  "tagNames": ["tag"],
  "assets": [
    {
      "assetId": "019ffb4f-4444-7aaa-8bbb-444444444444",
      "role": "PRIMARY_VIDEO",
      "relativePath": "Actress - [START-001].mp4",
      "storageKey": "fixture-joke-video",
      "tagNames": ["tag"]
    }
  ]
}
```

Required: `eventId`, `eventType`, `occurredAt`, `operationId`, `batchId`, `subjectId`,
`subjectVersion`, `region`, `subjectType`, `identityKey`, `actressNames`, `tagNames`, `assets`.
Payload không chứa absolute filesystem path.

`batchId` do Catalog tạo theo bounded output chunk và phải durable trong outbox. Serialized payload có
hard envelope mặc định `<= 900 KiB` để còn headroom dưới Kafka message limit; subject vượt envelope phải
được Catalog ghi `BLOCKED`/`SUBJECT_SNAPSHOT_TOO_LARGE`, không tự chia một v2 full snapshot thành nhiều
message thiếu contract.

## Semantics tối ưu cho approve 1M

- Event là full final snapshot, không phải delta.
- Catalog phát đúng một event cho mỗi `(operationId, subjectId)` sau khi coalesce toàn operation.
- `expectedSubjectCount` trong `CATALOG_COMMITTED` bằng số unique subject event v2 của operation.
- Query dedupe delivery theo `eventId`, dedupe business effect theo `(operationId, subjectId)` và chỉ áp dụng
  khi `subjectVersion` lớn hơn projection hiện tại. Event bằng/thấp hơn version hiện tại là no-op.
- Query upsert subject, replace asset snapshot, ghi processed marker, operation counter và search outbox
  trong cùng bounded transaction.

Conditional upsert mục tiêu:

```sql
ON CONFLICT (subject_id) DO UPDATE
SET ...
WHERE EXCLUDED.subject_version > query_subject.projection_version;
```

Không dùng `>=`: duplicate cùng version không được ghi lại projection hoặc tăng operation counter.

## Completion, retry và DLT

- Query chỉ phát `QUERY_DB_READY` khi unique `(operationId, subjectId)` processed count bằng
  `expectedSubjectCount` từ `media.approval.watermark.v1` và unresolved DLT bằng 0.
- Poison event đi DLT không chặn consumer xử lý record khác nhưng chuyển operation sang `BLOCKED`.
- Replay có thể tạo delivery `eventId` mới; version guard và business key vẫn ngăn duplicate effect.
- `traceparent`/`X-Correlation-Id` là optional Kafka headers; operation metadata bắt buộc nằm trong payload.
