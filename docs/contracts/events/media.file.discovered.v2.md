# `media.file.discovered.v2`

## Ownership

- Producer: `scan-service` transactional outbox sau decision `APPROVE`.
- Consumer: `catalog-service`.
- Topic: `media.file.discovered.v2`.
- Partition key: `region:subjectType:identityKey`.

## Payload

```json
{
  "eventId": "UUID",
  "eventType": "media.file.discovered.v2",
  "timestamp": "2026-08-01T00:00:00Z",
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
  "role": "PRIMARY_VIDEO",
  "storageKey": "fixture-joke-video",
  "relativePath": "Actress - [START-001].mp4"
}
```

`role` may be `null` for an album candidate. `storageKey` is a logical root key; absolute filesystem paths
are never emitted. `eventId` is the idempotency key and is deduplicated by Catalog in the same transaction
as canonical state changes.

## Compatibility and failure

- v2 là contract runtime duy nhất của SC-01 sau khi reset dữ liệu/E2E; event type khác v2 bị reject và đưa vào DLT.
- Delivery is at-least-once. Kafka retry is two retries after one second; unrecoverable records go to
  `<source-topic>.DLT`; SC-01 observer theo dõi `media.file.discovered.v2.DLT`.
- Correlation/trace context is carried in Kafka headers, not JSON. Unknown event versions fail and are retained
  in the corresponding DLT for operator action.
