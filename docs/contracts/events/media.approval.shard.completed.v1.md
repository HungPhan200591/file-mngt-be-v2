# `media.approval.shard.completed.v1`

Status: **APPROVED TARGET — FT-059 implementation pending**
Owner: `scan-service` → `catalog-service`
Feature: [FT-059](../../features/059-catalog-logical-shard-completion/03-plan.md)
Decision: [ADR-006](../../adr/ADR-006-logical-completion-shards.md)

## Mục đích và ownership

Control event cardinality thấp xác nhận một logical subject shard của approval operation đã commit đủ discovery
records trong `scan_db`. Catalog dùng marker này cùng durable unique input count để seal và reconcile shard sớm,
không đợi global operation barrier.

- Producer: `scan-service`, transactional outbox cùng shard completion checkpoint.
- Consumer: `catalog-service`.
- Topic/event type: `media.approval.shard.completed.v1`.
- Partition key: `operationId:completionShardId`.
- Delivery: at-least-once; DLT: `media.approval.shard.completed.v1.DLT`.
- Idempotency: `eventId` cho delivery; business uniqueness
  `(operationId, completionShardId, partitioningVersion)`.

Event này không thay thế global `media.approval.watermark.v1`. Scan vẫn phát `APPROVAL_COMMITTED` sau khi mọi
shard hoàn tất; Catalog vẫn phát `CATALOG_COMMITTED` sau global exact convergence và final broker ACK.

## Payload

```json
{
  "eventId": "019ffc00-1111-7aaa-8bbb-111111111111",
  "eventType": "media.approval.shard.completed.v1",
  "operationId": "019ffc00-2222-7aaa-8bbb-222222222222",
  "scanRunId": "019ffc00-3333-7aaa-8bbb-333333333333",
  "partitioningVersion": "SUBJECT_KEY_MD5_12_RANGE_V1",
  "completionShardId": 24,
  "completionShardCount": 64,
  "expectedRecordCount": 15625,
  "committedRecordCount": 15625,
  "sourceBatchCount": 1,
  "occurredAt": "2026-08-22T12:00:15Z"
}
```

Required: `eventId`, `eventType`, `operationId`, `scanRunId`, `partitioningVersion`, `completionShardId`,
`completionShardCount`, `expectedRecordCount`, `committedRecordCount`, `sourceBatchCount`, `occurredAt`.

Validation:

- `eventType` phải đúng literal `media.approval.shard.completed.v1`.
- `completionShardCount` là power of two trong `1..256`; candidate 64 không phải business invariant.
- `0 <= completionShardId < completionShardCount`.
- `expectedRecordCount >= 0` và `committedRecordCount = expectedRecordCount`.
- Zero-record shard vẫn phát một marker với hai count bằng `0`; nhờ đó Catalog xác nhận đủ đúng
  `completionShardCount` manifest, không suy đoán shard bị thiếu.
- Tổng `expectedRecordCount` của mọi shard phải bằng `expectedDiscoveryRecordCount` trong global
  `APPROVAL_COMMITTED`; mismatch block operation.

## Routing algorithm

V1 dùng canonical Kafka partition key của discovery event:

```text
subjectKey = region + ":" + subjectType + ":" + identityKey
digest = MD5(UTF-8(subjectKey))
routingBucket = ((digest[0] & 0xff) << 4) | ((digest[1] & 0xf0) >> 4)
completionShardId = floor(routingBucket * completionShardCount / 4096)
partitioningVersion = SUBJECT_KEY_MD5_12_RANGE_V1
```

Field đã nằm trong `media.file.discovered.v2`; không thêm `completionShardId` vào 1M data payload. Scan và
Catalog dùng shared router/golden vectors. MD5 chỉ là stable distribution hash, không phải security primitive.

Golden vectors với `completionShardCount=64`:

| subjectKey | MD5 | routingBucket | shard |
| --- | --- | ---: | ---: |
| `JOKE:VIDEO:START-001` | `63d369966b6d6f952f062b63414c03f7` | 1597 | 24 |
| `USE:VIDEO:USE:ACTRESS:TITLE:STUDIO` | `3859b16139467826ae35464f9b3f8fc9` | 901 | 14 |
| `USE:ALBUM:album-001` | `b14494d68d8b44fa8dc8684f7ddae2f9` | 2836 | 44 |

## Completion protocol

1. Scan accept operation và persist immutable `partitioningVersion`/`completionShardCount`.
2. Mỗi Scan shard worker xử lý only proposals có routing bucket thuộc shard; decision, discovery outbox và
   checkpoint commit theo bounded chunk.
3. Transaction cuối của shard verify exact count, mark ledger `COMPLETED` và insert marker vào Scan outbox.
4. Catalog có thể nhận marker trước hoặc sau data. Nó persist marker idempotent và chỉ seal shard khi unique
   discovery count bằng expected count, routing config khớp và shard DLT count bằng 0.
5. Catalog reconcile/publish final subject snapshots theo bounded page. Subject trong shard khác không chặn.
6. Global `APPROVAL_COMMITTED`/`CATALOG_COMMITTED` vẫn enforce tổng shard counts và terminal readiness.

Không dựa vào cross-topic ordering hoặc Kafka partition number. Marker publish sớm không đồng nghĩa data đã được
Catalog consume; equality gate mới là authority.

## Retry, conflict và compatibility

- Duplicate marker có cùng routing/count là no-op.
- Cùng business key nhưng khác count, shard count hoặc routing version chuyển operation sang
  `BLOCKED/CATALOG_SHARD_MANIFEST_CONFLICT`; không last-write-wins.
- Payload invalid/unknown version đi DLT và block operation; transient database/broker failure retry bounded.
- Marker DLT publication failure không được commit source offset; DLT phải provision trước rollout.
- New unique discovery sau shard seal là `CATALOG_SHARD_LATE_INPUT`; duplicate `eventId` vẫn no-op.
- Contract additive: old operation giữ processing version global-only; new operation bắt buộc shard protocol.
  Không đổi protocol giữa một operation và không dual-process cùng input bằng hai version.
- Tracing `traceparent`/`X-Correlation-Id` nằm trong optional Kafka headers, không bắt buộc trong payload.
