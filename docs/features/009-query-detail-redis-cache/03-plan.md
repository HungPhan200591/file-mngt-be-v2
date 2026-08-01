# 009 Query detail Redis cache — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `query-service`; Redis detail namespace do Query sở hữu, PostgreSQL vẫn là source of truth.
- Scope/files: Query dependency/config, cache DTO/adapter/use case, projection invalidation listener, metrics, PostgreSQL + Redis Testcontainers và tài liệu vận hành.
- Must preserve: REST/Kafka contract không đổi; Redis lỗi vẫn trả detail từ PostgreSQL; chỉ evict snapshot mới; không cache JPA entity/not-found/search; version/port đã pin; source dưới 500 dòng.
- Read on demand: Query context, coding rules, Feature 007 projection flow, Query OpenAPI, ADR-004, official Spring Data Redis 4 docs và HLD trong Design.

## Bước triển khai

1. Dependency/config: thêm Spring Boot Redis starter không lặp version; cấu hình host `localhost:18112`, TTL 10 phút, timeout ngắn và enable flag cho test/fallback.
2. Cache adapter: tạo key factory, immutable JSON cache entry và RedisTemplate serializer rõ ràng; implement get/put/evict best-effort cùng metrics.
3. Detail use case: chuyển controller detail sang cache-aside service; miss đọc `QueryProjectionService`, map DTO rồi put với TTL; giữ nguyên not-found/response.
4. Invalidation: chỉ khi projection version mới được áp dụng thì publish event nội bộ; listener `AFTER_COMMIT` evict subject key mà không ảnh hưởng Kafka transaction/fallback.
5. Verification: Testcontainers PostgreSQL + Redis cho miss → put → hit, TTL/config, version update eviction, duplicate/stale không evict và Redis unavailable fallback; kiểm tra metrics.
6. Operation/docs: bổ sung local Redis cache config, metric names và cách tắt cache; cập nhật Plan/Status sau evidence, không đổi OpenAPI nếu response giữ nguyên.

## Kiểm tra

- Static: `spotless:apply`, `git diff --check`, source dưới 500 dòng, dependency/version/config audit và link/HLD review.
- Query integration: detail contract cũ không đổi; cache miss/hit, JSON round-trip, eviction after commit và Redis failure fallback.
- Regression: chạy toàn bộ Query PostgreSQL + Elasticsearch tests để cache không ảnh hưởng search/rebuild/outbox.
- Runtime sau khi người dùng restart Query: gọi detail lặp lại, kiểm tra Actuator cache hit/miss và xác nhận Redis tắt vẫn trả 200 từ PostgreSQL.

## Rollout và rollback

- Rollout local với cache enabled; quan sát hit rate/error/latency trước khi điều chỉnh TTL hoặc mở rộng phạm vi.
- Rollback bằng `QUERY_DETAIL_CACHE_ENABLED=false` hoặc bỏ cache adapter; PostgreSQL projection/REST contract không đổi, dữ liệu Redis có thể hết hạn tự nhiên.

## Tài liệu cần cập nhật

- `docs/STATUS.md`, `manual/operations/local-runtime.md` và Query context chỉ khi ownership/invariant thay đổi; không tạo ADR mới vì Redis cache ownership đã được kiến trúc/context chốt.
- Không đổi Query OpenAPI hay event contract trong feature này.
