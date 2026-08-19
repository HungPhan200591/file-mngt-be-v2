# `media.approval.watermark.v1`

Status: **ACTIVE — contract target của SC-01 BT-09A**  
Owner theo stage: `scan-service` → `catalog-service` → `query-service`  
SLO owner: [SC-01 performance SLO](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/07-performance-slo-and-benchmarks.md)

## Mục đích

Control event cardinality thấp để xác nhận một operation approve 1.000.000 records đã hoàn tất từng
stage. Không phát progress event theo từng record; mỗi service chỉ cập nhật counter theo bounded batch và
phát một watermark khi stage của mình hội tụ.

- Topic: `media.approval.watermark.v1`.
- Partition key: `operationId`.
- Delivery: at-least-once; consumer dedupe theo `eventId` và chỉ tiến state theo `stageSequence` lớn hơn.
- Mọi stage event phải được ghi cùng transactional outbox với durable stage state của service phát.

## Lifecycle và SLO boundary

| State/watermark | Owner | Điều kiện |
| --- | --- | --- |
| `ACCEPTED` | Scan | Operation row được commit O(1); HTTP trả `202`; `acceptedAt` là thời điểm bắt đầu SLI-03. |
| `APPROVAL_COMMITTED` (`stageSequence=10`) | Scan | Toàn bộ decision + discovery outbox đã commit theo bounded chunk; `scanCommittedRecordCount = expectedRecordCount`. |
| `CATALOG_COMMITTED` (`stageSequence=20`) | Catalog | Đã xử lý đủ unique discovery event, không còn unresolved DLT và đã tạo đúng một final snapshot cho mỗi affected subject. |
| `QUERY_DB_READY` (`stageSequence=30`) | Query | Đã commit đủ unique subject snapshot vào `query_db`, durable watermark và search outbox; `projectedSubjectCount = expectedSubjectCount`, unresolved DLT bằng 0. |
| `SEARCH_READY` (`stageSequence=40`) | Query search worker | Đã index đủ search document của operation; không thuộc critical path `QUERY_DB_READY`. |
| `BLOCKED` | Stage owner | Có unresolved DLT/retryable dependency failure; không được phát ready watermark. |
| `FAILED` / `CANCELLED` | Stage owner | Terminal state; không được tự chuyển thành ready nếu không có operation/replay mới hợp lệ. |

SLO approve bắt đầu tại `acceptedAt` và kết thúc tại `queryDbReadyAt` do Query commit. Độ trễ Scan
project status nhận lại event không được cộng vào SLI và cũng không được dùng để che latency thực.

## Payload

```json
{
  "eventId": "019ffb4f-1111-7aaa-8bbb-111111111111",
  "eventType": "media.approval.watermark.v1",
  "operationId": "019ffb4f-2222-7aaa-8bbb-222222222222",
  "scanRunId": "019ffb4f-3333-7aaa-8bbb-333333333333",
  "stage": "CATALOG_COMMITTED",
  "stageSequence": 20,
  "expectedRecordCount": 1000000,
  "expectedDiscoveryRecordCount": 1000000,
  "expectedRemovalRecordCount": 0,
  "scanCommittedRecordCount": 1000000,
  "catalogProcessedRecordCount": 1000000,
  "expectedSubjectCount": 148321,
  "projectedSubjectCount": null,
  "unresolvedDltCount": 0,
  "sourceBatchCount": 500,
  "outputBatchCount": 149,
  "occurredAt": "2026-08-15T22:00:15Z",
  "failureCode": null
}
```

Required chung: `eventId`, `eventType`, `operationId`, `scanRunId`, `stage`, `stageSequence`,
`expectedRecordCount`, `unresolvedDltCount`, `occurredAt`.

- `APPROVAL_COMMITTED` yêu cầu `scanCommittedRecordCount`, `sourceBatchCount`,
  `expectedDiscoveryRecordCount` và
  `expectedRemovalRecordCount`; tổng hai field phải bằng `expectedRecordCount`. BT-09D/FT-054 chỉ qualify
  discovery-only workload và chuyển mixed operation sang `BLOCKED/UNSUPPORTED_MIXED_CATALOG_OPERATION`.
- `CATALOG_COMMITTED` yêu cầu `catalogProcessedRecordCount`, `expectedSubjectCount` và `outputBatchCount`.
- `QUERY_DB_READY` yêu cầu `expectedSubjectCount = projectedSubjectCount`.
- `BLOCKED`/`FAILED` yêu cầu `failureCode`; không đưa exception message/path vào metric label hay payload public.

`stageSequence` tăng đơn điệu theo operation: `10 → 20 → 30 → 40`. `BLOCKED`, `FAILED` và `CANCELLED`
dùng sequence lớn hơn stage cuối đã commit; consumer giữ trạng thái có sequence cao nhất và dedupe eventId.

## Completion protocol

1. Scan commit operation row `ACCEPTED`, trả `202`, sau đó ghi decision + `media.file.discovered.v2`
   outbox theo bounded chunk.
2. Scan phát `APPROVAL_COMMITTED` sau khi đếm đủ unique outbox record đã commit.
3. Catalog đếm unique discovery event theo `(operationId, eventId)`. Chỉ khi đủ `expectedRecordCount`,
   Catalog mới chốt `expectedSubjectCount` và phát đúng một `media.subject.changed.v2` final snapshot cho
   mỗi `(operationId, subjectId)`.
4. Catalog phát `CATALOG_COMMITTED`. Query đếm unique final snapshot theo `(operationId, subjectId)` và
   chỉ commit `QUERY_DB_READY` khi đủ `expectedSubjectCount`, không có unresolved DLT.
5. Scan consume control topic để materialize status cho tracking API. Mỗi service vẫn sở hữu database và
   stage truth của chính mình; không có cross-database join/write.

Counter được flush theo bounded batch, không `UPDATE` operation row cho từng record. Một stage manifest có
thể đến trước data event ở topic khác; equality gate và dedupe làm stage chờ hội tụ thay vì dựa vào global
Kafka ordering.

## Cache và search

- Không pipeline `DEL` theo từng subject trên critical path.
- Query tạo `cacheGeneration` mới cùng durable operation state; cache key mang generation.
- Redis chỉ switch generation O(1). Redis lỗi thì cache bypass/fallback `query_db`, không chặn
  `QUERY_DB_READY`.
- Search outbox được commit cùng projection; `SEARCH_READY` được phát riêng sau Elasticsearch bulk.

## Retry, DLT và replay

- Poison event được cô lập để batch tiếp tục, nhưng operation chuyển `BLOCKED` và không được phát ready.
- Replay giữ `operationId`, tạo `eventId` mới cho lần delivery và vẫn dedupe business effect bằng
  `(operationId, subjectId)` cùng `subjectVersion`.
- Data loss, duplicate canonical effect, sai cardinality hoặc unresolved DLT là SLO failure dù latency đạt.

## Headers

`traceparent` và `X-Correlation-Id` là optional tracing headers. `operationId`, `batchId` và completion
cardinality phải nằm trong durable payload/outbox để replay không phụ thuộc transient Kafka headers.
