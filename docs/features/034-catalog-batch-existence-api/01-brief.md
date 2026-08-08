# FT-034 — Catalog batch existence API

Owner: `catalog-service` / `catalog_db`; consumer tương lai: `scan-service` trong BT-05.

Nguồn bài toán: [SC-01 BT-04](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-04--catalog-batch-existence-api).

## Vấn đề

Inventory của Scan đã loại phần lớn file không đổi, nhưng với file mới hoặc đổi fingerprint, Scan chưa có
cách hỏi Catalog theo batch xem locator hoặc canonical subject đã tồn tại. Nếu tự suy luận từ `scan_db`,
đọc chéo `catalog_db` hoặc gọi từng item, boundary ownership bị phá hoặc throughput bị biến thành N+1.

Catalog hiện chỉ unique locator trong phạm vi một subject qua
`(subject_id, COALESCE(storage_key, ''), relative_path)`. BT-04 cần lookup locator canonical toàn cục theo
`storageKey + relativePath`, đồng thời phát hiện dữ liệu xung đột thay vì gắn asset vào sai subject.

## Mục tiêu và acceptance criteria

- Thêm OpenAPI nội bộ version `v1` cho `POST /internal/v2/catalog/scan-existence`; endpoint chỉ gọi trực
  tiếp Catalog, không route qua Gateway.
- Request chứa `scanRunId` và từ 1 đến 500 candidate; mỗi `clientRef` là duy nhất trong request và nhận
  đúng một result để caller correlate mà không phụ thuộc thứ tự.
- Catalog phân loại từng candidate thành `EXACT_ASSET_EXISTS`, `EXISTING_SUBJECT_NEW_ASSET`,
  `NEW_SUBJECT` hoặc `CONFLICT` theo locator canonical và identity
  `(region, subjectType, identityKey)`.
- `EXACT_ASSET_EXISTS` chỉ áp dụng khi locator, subject identity và asset role cùng khớp. Locator thuộc
  subject khác, role khác hoặc subject đã có `PRIMARY_VIDEO` khác phải trả `CONFLICT`.
- `catalog_db` có unique partial index toàn cục `(storage_key, relative_path) WHERE storage_key IS NOT
  NULL`; asset legacy có `storage_key IS NULL` không được coi là exact locator match.
- Lookup dùng truy vấn set-based cho cả batch, không query từng candidate và không load toàn bộ Catalog
  vào memory.
- Endpoint read-only: không tạo subject/asset, không ghi outbox và không coi `scanRunId` là idempotency
  key. Retry an toàn nhưng kết quả có thể đổi nếu Catalog thay đổi giữa hai lần gọi.
- Request validation lỗi trả `400 application/problem+json`; lỗi dependency database khiến endpoint
  tạm không phục vụ trả `503 application/problem+json`; không trả partial result cho request invalid.
- Có integration test trực tiếp Catalog cho đủ bốn classification, giới hạn batch, duplicate
  `clientRef`, locator legacy, xung đột role/subject và request retry không tạo mutation.
- Kết thúc FT-034 vẫn chưa có Scan client và chưa thay đổi proposal/outbox/event behavior.

## Ngoài phạm vi

- Gọi endpoint từ Scan, retry/timeout/circuit breaker phía Scan và map classification thành proposal:
  thuộc BT-05.
- Thay đổi `media.file.discovered.v1/v2`, approval, Catalog consumer hoặc write-side idempotency.
- Tự sửa/xóa locator trùng, backfill `storage_key` legacy hoặc import/migration dữ liệu thật.
- Cache, Kafka, sharding, scale-out hay tuning batch vượt 500 khi chưa có benchmark.
- Public/Gateway API hoặc authentication/authorization mới cho internal service traffic.

## Câu hỏi/rủi ro mở

Không còn câu hỏi nghiệp vụ hoặc kiến trúc chặn code. Các rủi ro đã có hướng xử lý:

- Unique index sẽ fail migration nếu dữ liệu hiện tại có locator non-null trùng giữa các subject; phải
  audit và sửa fixture/data có chủ đích, không tự chọn record để xóa.
- Classification là snapshot tư vấn trước approval, không khóa Catalog cho tới lúc event đến. Consumer
  Catalog và database constraint vẫn là authority cuối để chịu concurrent ingest.
- Chưa có latency/throughput baseline cho batch 500; chỉ được tuyên bố bounded và set-based cho tới khi
  có integration benchmark/evidence.
