# Backend V2 — Tóm tắt

## Mục tiêu

Viết lại backend từ đầu để vừa giải quyết mô hình media, vừa học microservice, event-driven và tối ưu hiệu năng trên một bài toán thật.

V2 chạy song song với V1. Không xóa hay sửa dữ liệu V1 trong quá trình xây dựng; frontend chuyển dần sang `/api/v2` sau khi từng luồng đã được kiểm chứng.

## Stack chính

- Java 25 và Spring Boot 4.0.3.
- Maven multi-module monorepo.
- PostgreSQL + Flyway.
- Apache Kafka ở chế độ KRaft và Redis.
- Docker Compose cho local development.
- OpenAPI, Testcontainers, JUnit 5, AssertJ.
- Micrometer, OpenTelemetry, Prometheus/Grafana và ELK (Elasticsearch, Logstash, Kibana) ở giai đoạn observability.

Port local V2 dùng dải riêng theo [ADR-004](../adr/ADR-004-local-port-allocation.md); không dùng lại port V1 hay port host mặc định của hạ tầng.

Spring Boot 4.0.3 được chọn vì tài liệu chính thức xác nhận hỗ trợ đến Java 25 và có integration sẵn cho Kafka, Redis cùng Docker Compose service connections.

## Kiến trúc mục tiêu

- `gateway-service`: cổng business API cho frontend, routing và correlation ID.
- `catalog-service`: nguồn dữ liệu chuẩn cho subject, asset, actress, studio và tag.
- `scan-service`: scan filesystem, parse filename và tạo proposal review.
- `media-worker`: xử lý nền cho thumbnail, GIF, metadata kỹ thuật và hash file.
- `query-service`: read model tối ưu cho Gallery Web, Media Library và filter.

Kafka làm event bus và work queue. Redis chỉ là cache/read optimization. PostgreSQL là source of truth; mỗi service sở hữu database/user riêng trong cùng một PostgreSQL instance để local đơn giản. Nginx là media delivery plane read-only: browser tải IMAGE/GIF/VIDEO trực tiếp từ root map logical theo [ADR-005](../adr/ADR-005-nginx-direct-media-delivery.md), không qua Gateway hoặc Media Worker.

## Mô hình nghiệp vụ lõi

- `media_subject` có loại `VIDEO` hoặc `ALBUM`.
- `media_asset` là file vật lý thuộc subject, role `PRIMARY_VIDEO`, `VIDEO`, `IMAGE` hoặc `GIF`.
- JOKE liên kết video/ảnh/GIF bằng code.
- USE liên kết Syncdroid/FullPics/GIF bằng basename chuẩn hóa.
- USE Album có identity theo folder; có thể có liên kết tùy chọn `FULL_ALBUM_OF` tới video Syncdroid. Liên kết này không gộp subject/asset và case không chắc chắn phải review.

## Trạng thái nghiệp vụ

- Dùng `status` khi nó giúp hành vi nghiệp vụ, code hoặc UI dễ hiểu hơn.
- Không tạo status nếu quan hệ, boolean, timestamp hoặc error detail đã biểu đạt đủ ý nghĩa; tránh hai status cùng nghĩa hoặc state machine chỉ để mô tả tiến độ.
- Tiến độ/lỗi kỹ thuật có thể dùng `started_at`, `finished_at`, `attempt_count`, `last_error` khi phù hợp; không ép mọi thông tin này vào status.

## Pattern và Java 25

Quy ước triển khai Java nằm tại [03-CODING_RULES.md](./03-CODING_RULES.md); đọc khi viết hoặc review code, không cần nạp cho task tài liệu thuần túy.

- Hexagonal Architecture và DDD ở mức vừa đủ.
- Strategy + Registry cho parser JOKE/USE.
- Factory, Repository, Specification, Adapter.
- CQRS-lite, Transactional Outbox, idempotent consumer, dead-letter topic.
- Record, sealed interface, pattern matching, generic, functional interface và virtual thread.
- Cache-aside, benchmark, tracing và profiling.

## Giới hạn để vẫn làm nhanh

- Không triển khai Kubernetes ở luồng chính.
- Không dùng RabbitMQ vì Kafka đáp ứng event bus và work queue.
- Không authentication/permission phức tạp ở bản đầu.
- Không chia service riêng cho Actress, Studio hoặc Tag.
- Không distributed transaction; chấp nhận eventual consistency ở read model.
- Mỗi feature thêm một nhóm công nghệ có bài toán thật, không triển khai mọi công nghệ cùng lúc.
- Elasticsearch phục vụ cả structured log và media search projection, nhưng tách logs data stream với media search index. Query là owner duy nhất của business search; Catalog/PostgreSQL vẫn là nguồn dữ liệu chuẩn.

Chi tiết kiến trúc và phase nền tảng nằm tại [02-PLAN.md](./02-PLAN.md). Kiến trúc end-to-end riêng cho
workload SC-01 được chốt ở [04-SC-01-1M-scan-approve-end-to-end-architecture.md](./04-SC-01-1M-scan-approve-end-to-end-architecture.md);
đây là proposal chuẩn bị triển khai, không thay thế Plan/contract của từng BT-09.
