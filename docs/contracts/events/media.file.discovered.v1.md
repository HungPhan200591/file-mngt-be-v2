# `media.file.discovered.v1`

## Ownership

- Producer: `scan-service` outbox publisher, sau decision `APPROVE`.
- Consumer: `catalog-service`.
- Topic: `media.file.discovered.v1`.
- Partition key: `region:subjectType:identityKey`.

## Payload v1

```json
{
  "eventId": "UUID",
  "eventType": "media.file.discovered.v1",
  "occurredAt": "2026-08-01T00:00:00Z",
  "scanRunId": "UUID",
  "proposalId": "UUID",
  "region": "JOKE",
  "subjectType": "VIDEO",
  "identityKey": "START-001",
  "displayTitle": "Actress - [START-001]",
  "assetRole": "PRIMARY_VIDEO",
  "sourceRootKey": "fixture-joke-video",
  "sourceRelativePath": "Actress - [START-001].mp4"
}
```

`assetRole` có thể là `null` với candidate album không có file asset. Không có absolute filesystem path trong payload.

## Delivery và compatibility

- Delivery at-least-once. Catalog dedupe bằng `eventId` trong transaction với upsert canonical subject/asset.
- Producer lưu outbox trước khi publish; `published_at` chỉ được set sau broker acknowledgement. Retry dùng cùng `eventId`.
- `X-Correlation-Id` và W3C `traceparent` là Kafka headers, không phải field JSON. Producer lưu metadata này cùng outbox transaction để relay khôi phục context trước khi publish; consumer chấp nhận record cũ không có header.
- Consumer lỗi thì xử lý tối đa 3 lần với backoff 1 giây, sau đó publish record gốc sang
  `media.file.discovered.v1.DLT`. Không sửa payload v1 theo cách breaking; thay đổi breaking tạo event version mới.

## Mapping Catalog

- `region`, `subjectType`, `identityKey`, `displayTitle` tạo/tìm canonical subject.
- Nếu có `assetRole`, thêm asset từ `sourceRootKey + sourceRelativePath` khi chưa tồn tại cùng locator trong subject.
- Catalog persist `sourceRootKey` thành nullable `storageKey` của asset; Catalog không đọc filesystem root của Scan.
