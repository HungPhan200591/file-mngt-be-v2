# 007 Query subject projection

Owner: `query-service`

## Vấn đề

Catalog đã phát snapshot `media.subject.changed.v1` nhưng chưa có read model cho Gallery Web/Library gọi độc lập với canonical write model.

## Mục tiêu và acceptance criteria

- Query consume idempotent event Catalog, chỉ áp dụng `subjectVersion` mới hơn và lưu projection trong `query_db`.
- API Query đọc detail/list với region, type, text search, order và pagination; response có projection version/timestamp.
- Retry/DLT cho consumer; không đọc/ghi `catalog_db`, chưa dùng Elasticsearch/Redis.

## Ngoài phạm vi

- Elasticsearch, fuzzy/autocomplete, Redis cache, rebuild/backfill, Gateway routing và frontend cutover.
