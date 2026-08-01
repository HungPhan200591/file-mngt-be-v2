# Query Service context

## Scope

Read model tối ưu cho Gallery Web, Media Library, filter, card và search.

## Owns

- Database `query_db`: projection và processed-event Query.
- Kafka consumer dựng lại projection từ Catalog/Worker events.
- REST API search, filter, order, pagination và detail read model.
- Elasticsearch media search index; hỗ trợ full-text, fuzzy match và autocomplete.
- Redis key/TTL/cache invalidation policy cho query.

## Invariants

- Không ghi ngược Catalog.
- Projection eventual consistent; response phải có metadata cần thiết để UI hiểu trạng thái.
- Elasticsearch là search projection, không phải canonical data. UI chỉ gọi Query API, không gọi Elasticsearch trực tiếp.
- Redis miss hoặc Redis hỏng vẫn đọc PostgreSQL.
- Không join chéo schema/service lúc request.
