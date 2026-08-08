# Nợ kỹ thuật Backend V2

Updated: 2026-08-08

## Backlog đang mở

| ID | Owner | Mức độ | Nợ còn mở | Điều kiện trả nợ |
| --- | --- | --- | --- | --- |
| `TD-004` | `platform/observability` | LOW | Prometheus `scan_run_duration_seconds` chưa mang trace/correlation context. | Có use case tracing metric liên service; cấu hình Observation Handler phù hợp. |
| `TD-005` | `catalog-service` | LOW | Một phần metadata Catalog Event còn dùng raw `String` thay vì envelope schema versioning chặt. | Khi đổi Catalog event contract, chuẩn hóa tại `platform/event-contracts` theo migration tương thích. |

Chi tiết triển khai và bằng chứng trả nợ thuộc Feature Plan/commit tương ứng; file này chỉ giữ backlog còn mở.
