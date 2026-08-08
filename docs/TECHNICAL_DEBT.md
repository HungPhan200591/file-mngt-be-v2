# Nợ kỹ thuật Backend V2

Updated: 2026-08-08

## Backlog đang mở

| ID | Owner | Mức độ | Nợ còn mở | Điều kiện trả nợ |
| --- | --- | --- | --- | --- |
| `TD-004` | `platform/observability` | LOW | Prometheus `scan_run_duration_seconds` chưa mang trace/correlation context. | Có use case tracing metric liên service; cấu hình Observation Handler phù hợp. |
| `TD-005` | `catalog-service` | LOW | Một phần metadata Catalog Event còn dùng raw `String` thay vì envelope schema versioning chặt. | Khi đổi Catalog event contract, chuẩn hóa tại `platform/event-contracts` theo migration tương thích. |
| `TD-006` | `scan-service` | MEDIUM | Chưa có targeted recheck/rescan cho một file đang nằm trong Issue worklist sau khi người dùng sửa thủ công filename hoặc nội dung. Hiện chỉ có full-root incremental scan. | Thiết kế job recheck riêng theo `issueId` hoặc danh sách issue: xác minh file hiện tại trên đĩa, lưu observation proposal/issue mới, cập nhật inventory an toàn, có tiến độ bất đồng bộ và idempotency; không gọi full-root scan trá hình hoặc làm hỏng lease/reconciliation. |

Chi tiết triển khai và bằng chứng trả nợ thuộc Feature Plan/commit tương ứng; file này chỉ giữ backlog còn mở.
