# ADR-004: Dải port local riêng cho Backend V2

Status: ACCEPTED
Date: 2026-08-01

## Context

V1 và các công cụ local đã dùng các port quen thuộc như `8081`, `8888`, `5432`, `5433`, `6380`. Bootstrap V2 không được chiếm các port này hoặc tự chọn lại port chuẩn của một công nghệ, vì làm hai hệ thống không thể chạy song song.

## Decision

Mọi **host port** của Backend V2 nằm trong dải dành riêng `18100–18117`. Port nội bộ Docker vẫn dùng port chuẩn để service trong cùng Compose giao tiếp đơn giản. Đây là source of truth duy nhất cho port local V2.

| Mục đích | Host port |
| --- | ---: |
| Gateway HTTP | 18100 |
| Catalog HTTP | 18101 |
| Scan HTTP | 18102 |
| Query HTTP | 18103 |
| Media Worker management | 18104 |
| PostgreSQL | 18110 |
| Kafka external listener | 18111 |
| Redis | 18112 |
| Elasticsearch (future) | 18113 |
| Kibana (future) | 18114 |
| Logstash input (future) | 18115 |
| Prometheus (future) | 18116 |
| Grafana (future) | 18117 |

Quy tắc triển khai:

- Cấu hình app và Compose dùng environment variable với default đúng bảng trên.
- Không dùng `8080–808x`, `5432–5433`, `6379–6380`, `8888`, `9092`, `9200`, `5601` làm host port V2.
- Trước khi thêm một host port mới, kiểm tra bảng này và port đang listen; nếu cần mở rộng dải hoặc đổi giá trị, cập nhật ADR này trước rồi mới đổi code/config.
- Không sao chép bảng port vào context service. Tài liệu khác chỉ liên kết đến ADR này.

## Consequences

- V1 và V2 chạy song song mà không phải sửa port V1.
- URL local V2 dễ nhận diện bằng prefix `181`.
- Khi bật observability ở phase sau, port đã được giữ trước nên Compose không tự phát sinh port khác.
