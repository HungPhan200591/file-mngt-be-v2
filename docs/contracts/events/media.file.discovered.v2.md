# `media.file.discovered.v2`

## Ownership

- Producer: `scan-service` transactional outbox sau decision `APPROVE`.
- Consumer: `catalog-service`.
- Topic: `media.file.discovered.v2`.
- Partition key: `region:subjectType:identityKey`.
- Topic cần nhiều partition để scale consumer; local fresh environment mặc định 12 partition và Catalog concurrency 8.
  Tăng partition giữ ordering theo key từ thời điểm thay đổi nhưng không giữ mapping partition cũ, nên phải drain backlog trước rollout.

## Payload

```json
{
  "eventId": "UUID",
  "eventType": "media.file.discovered.v2",
  "timestamp": "2026-08-01T00:00:00Z",
  "operationId": "UUID",
  "batchId": "scan-output-00042",
  "scanRunId": "UUID",
  "proposalId": "UUID",
  "region": "JOKE",
  "subjectType": "VIDEO",
  "identityKey": "START-001",
  "baseCode": "START",
  "part": "001",
  "studioCode": "STUDIO",
  "displayTitle": "Actress - [START-001]",
  "actressNames": ["Actress"],
  "tagNames": ["tag"],
  "role": "VIDEO",
  "storageKey": "fixture-joke-video",
  "relativePath": "Actress - [START-001].mp4"
}
```

`role` may be `null` for an album candidate. `storageKey` is a logical root key; absolute filesystem paths
are never emitted. `eventId` is the idempotency key and is deduplicated by Catalog in the same transaction
as canonical state changes.

`operationId` và `batchId` là required cho approve operation và nullable cho single decision legacy.
Chúng phải nằm trong payload/outbox để
completion counter và replay không phụ thuộc transient Kafka headers. Catalog đếm unique input theo
`(operationId, eventId)` và đối chiếu `expectedRecordCount` từ `media.approval.watermark.v1`.

For video events, `tagNames` describes the candidate file. Catalog stores the tags on the asset,
elects exactly one `PRIMARY_VIDEO`, and materializes subject `tagNames` from that primary. An untagged
video outranks a tagged video; equal priority keeps the current primary. Scan producers should emit
`role=VIDEO`; Catalog still accepts legacy `PRIMARY_VIDEO` producers during compatibility rollout.
For `IMAGE`, `GIF` or `null` role events, Catalog ignores `tagNames` for primary election.

## Compatibility and failure

- v2 là contract runtime duy nhất của SC-01 sau khi reset dữ liệu/E2E; event type khác v2 bị reject và đưa vào DLT.
- Delivery is at-least-once. Payload/contract không hợp lệ là non-retryable và đi thẳng DLT. Lỗi database/broker
  tạm thời retry tối đa ba lần với exponential backoff `250ms → 500ms → 1s`, base jitter `100ms` được Spring
  scale theo interval và trần `2s`.
- `media.file.discovered.v2.DLT` được provision cùng số partition với source topic; recoverer giữ nguyên source
  partition. Nếu publish DLT lỗi, handler không commit source offset mà reseek để recovery có thể chạy lại.
- SC-01 observer theo dõi `media.file.discovered.v2.DLT`; poison record có `operationId` chuyển operation sang
  `BLOCKED/CATALOG_INPUT_DLT`.
- V2 là runtime target duy nhất của study environment; BT-09B/BT-09D thay thẳng producer/consumer và có thể
  reset local topic/data, không duy trì payload cũ thiếu operation metadata.
- Correlation/trace context is carried in Kafka headers, not JSON. Unknown event versions fail and are retained
  in the corresponding DLT for operator action.
