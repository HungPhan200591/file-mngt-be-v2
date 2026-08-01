# 008 Elasticsearch media search

Owner: `query-service`

## Vấn đề

Feature 007 mới tìm kiếm `contains-ignore-case` trên PostgreSQL. Gallery Web và Media Library cần full-text search, typo tolerance và gợi ý khi nhập, nhưng Catalog không được phụ thuộc Elasticsearch và frontend không được gọi Elasticsearch trực tiếp.

## Mục tiêu và acceptance criteria

- `query-service` duy trì một Elasticsearch document cho mỗi subject từ projection PostgreSQL hiện có; document cũ không được ghi đè document mới hơn.
- `GET /api/v2/query/subjects` dùng Elasticsearch khi có `search`, hỗ trợ exact filter `region`/`subjectType`, `RELEVANCE|CREATED_AT|TITLE`, fuzzy match và pagination; không có `search` vẫn dùng PostgreSQL.
- Thêm API autocomplete theo `identityKey` và `displayTitle`.
- Khi Elasticsearch không sẵn sàng, text search fallback về PostgreSQL và response chỉ rõ backend/fallback; detail và list không có text search vẫn hoạt động.
- Có rebuild idempotent từ `query_db` sang physical index mới rồi đổi alias; rebuild lỗi không thay alias đang phục vụ.
- Elasticsearch local dùng host port `18113`, image/client được pin cùng version; integration test dùng Testcontainers và E2E xác minh search từ luồng Scan → Catalog → Query.

## Ngoài phạm vi

- Actress, studio, tag và metadata chưa có trong `media.subject.changed.v1`; không mở rộng event contract trong feature này.
- Redis cache, frontend cutover, semantic/vector search, highlight nâng cao, synonym UI, production security/TLS và cluster nhiều node.
- ELK log pipeline, Logstash và Kibana; chúng dùng cùng cluster ở giai đoạn sau nhưng khác index/data stream.

## Câu hỏi/rủi ro mở

- Không còn quyết định chặn triển khai. Giới hạn đầu tiên là Elasticsearch `from/size` tối đa 10.000 hit; deep pagination bằng `search_after` để feature sau khi có nhu cầu thật.
