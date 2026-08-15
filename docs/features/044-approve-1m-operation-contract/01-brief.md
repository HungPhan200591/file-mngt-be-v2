# FT-044 — SC-01 BT-09A: Approve 1M Operation Contract & Watermark

Owner: `scan-service`, `catalog-service`, `query-service`, `gateway-service`  
Use case: [SC-01 Approve 1M Context](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md) — [BT-09A](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned)

## Mục tiêu

- Chốt lifecycle và completion protocol cho một operation approve 1.000.000 records từ Scan tới
  `QUERY_DB_READY` mà không dùng distributed transaction.
- Giữ critical path theo bounded batch, bulk persistence và control event cardinality thấp để hướng tới
  SLO P95 ≤ 30 giây, P99 ≤ 45 giây.
- Dùng `media.subject.changed.v2` làm contract duy nhất; bỏ runtime v1, không dual-publish/backward compatibility.

## Acceptance criteria

1. Scan commit operation row `ACCEPTED` O(1), trả `202 + operationId`; `acceptedAt` bắt đầu SLO.
2. `APPROVAL_COMMITTED` chỉ xuất hiện sau khi toàn bộ decision + discovery outbox được commit theo bounded chunk.
3. Catalog phát đúng một final subject snapshot v2 cho mỗi `(operationId, subjectId)` và chốt
   `expectedSubjectCount` sau khi đã nhận đủ `expectedRecordCount`.
4. Query chỉ phát `QUERY_DB_READY` khi projected count khớp expected subject count, durable watermark đã
   commit và unresolved DLT bằng 0.
5. Redis dùng cache generation switch O(1); Redis failure không chặn DB-ready. `SEARCH_READY` là async lane riêng.
6. Tracking API có owner rõ: Scan materialize control event vào `scan_db`; timestamp SLO kết thúc vẫn lấy từ
   Query stage commit, không lấy thời điểm status projector nhận event.
7. `BLOCKED`, `FAILED`, `CANCELLED` có đường ra rõ; poison event có thể được cô lập nhưng không được đánh dấu ready.

## Contract owner

- Operation protocol: [media.approval.watermark.v1](../../contracts/events/media.approval.watermark.v1.md).
- Catalog → Query snapshot: [media.subject.changed.v2](../../contracts/events/media.subject.changed.v2.md).
- Tracking REST: [Scan OpenAPI v1](../../contracts/openapi/scan-v1.yaml).

## Ngoài phạm vi

- Code Scan chunking/outbox (`BT-09B`), relay (`BT-09C`), Catalog coalesce (`BT-09D`) và Query bulk (`BT-09E`).
- Runtime benchmark/qualification (`BT-09G`).
- Mixed-version rollout. Study environment được phép reset Kafka/data local trước verification.
