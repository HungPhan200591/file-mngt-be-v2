# Query Service context

## Scope

Read model tối ưu cho Gallery Web, Media Library, filter, card và search.

## Owns

- Database `query_db`: projection và processed-event Query.
- Kafka consumer dựng lại projection từ Catalog/Worker events.
- Contract target SC-01 là `media.subject.changed.v2` batch projection với operation completion counter;
  runtime v1 sẽ được thay thẳng ở BT-09E, không duy trì dual consumer.
- REST API search, filter, order, pagination và detail read model.
- Gallery filter theo asset root/studio/actress/tag và `mediaUrl` Nginx được resolve từ locator + deployment root map;
  response trả `null` khi projection/root mapping chưa đủ, không suy diễn filesystem path.
- Gallery page dùng `/api/v2/query/videos`: mỗi video trong root là một card; subject không có video trong root dùng
  đúng một `IMAGE`/`GIF` làm card đại diện. Tag filter đọc tag asset đại diện; card trả selected video nếu có cùng
  toàn bộ `IMAGE`/`GIF` của subject để render carousel, còn detail vẫn theo subject.
- Elasticsearch media search index; hỗ trợ full-text, fuzzy match và autocomplete.
- Redis key/TTL/cache invalidation policy cho query.

## Invariants

- `media.subject.deleted.v1` xóa PostgreSQL projection, evict Redis sau commit và enqueue durable Elasticsearch delete trong cùng transaction.
- Subject tombstone theo version chặn snapshot cũ đến đảo thứ tự làm projection đã xóa sống lại.

- Không ghi ngược Catalog.
- Projection eventual consistent; response phải có metadata cần thiết để UI hiểu trạng thái.
- Elasticsearch là search projection, không phải canonical data. UI chỉ gọi Query API, không gọi Elasticsearch trực tiếp.
- Redis miss hoặc Redis hỏng vẫn đọc PostgreSQL.
- Không join chéo schema/service lúc request.
- Dùng `platform/observability` cho direct-request correlation MDC; expose Prometheus chỉ trên direct
  service port. Custom cache/search metrics phải dùng label cardinality thấp.
- ECS logs data stream tách biệt Elasticsearch media index; ELK ingest lỗi không được làm Query API lỗi.
