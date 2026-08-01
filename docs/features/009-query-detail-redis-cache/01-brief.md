# 009 Query detail Redis cache

Owner: `query-service`

## Vấn đề

`GET /api/v2/query/subjects/{id}` luôn hydrate subject và assets từ PostgreSQL. Đây là dữ liệu card/detail được đọc lặp lại nhiều lần khi Gallery Web hoặc Media Library mở preview, trong khi Query projection chỉ đổi khi nhận snapshot version mới. Redis đã có trong hạ tầng nhưng chưa có use case thực tế hay số liệu chứng minh hiệu quả.

## Mục tiêu và acceptance criteria

- Thêm cache-aside cho detail subject theo ID; cache hit không truy vấn PostgreSQL.
- Cache value là DTO JSON immutable, không serialize JPA entity; key có namespace/version và TTL cấu hình được.
- Khi Query áp dụng projection version mới, cache của subject đó bị evict sau commit; duplicate/stale event không tạo eviction không cần thiết.
- Redis miss, timeout, serialization error hoặc unavailable không làm detail API lỗi: Query đọc PostgreSQL và trả contract hiện tại.
- Có metrics hit, miss, put, eviction, error và latency để so sánh trước/sau cache.
- Integration test dùng PostgreSQL + Redis Testcontainers, bao phủ hit, miss, eviction và Redis failure fallback.

## Ngoài phạm vi

- Không cache Elasticsearch search result, list/filter page, autocomplete hoặc negative/not-found response.
- Không thêm distributed lock, cache stampede protection, Redis Cluster/Sentinel hay write-behind.
- Không đổi Query REST response, Kafka event, Catalog ownership hoặc frontend.

## Câu hỏi/rủi ro mở

- Không còn quyết định chặn triển khai. TTL mặc định ban đầu là 10 phút; chỉ điều chỉnh sau khi có hit rate và stale-risk thực tế.
