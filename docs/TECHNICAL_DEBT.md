# Nợ kỹ thuật Backend V2

Updated: 2026-08-11

## Backlog đang mở

| ID | Owner | Mức độ | Nợ còn mở | Điều kiện trả nợ |
| --- | --- | --- | --- | --- |
| `TD-004` | `platform/observability` | LOW | Prometheus `scan_run_duration_seconds` chưa mang trace/correlation context. | Có use case tracing metric liên service; cấu hình Observation Handler phù hợp. |
| `TD-005` | `catalog-service` | LOW | Một phần metadata Catalog Event còn dùng raw `String` thay vì envelope schema versioning chặt. | Khi đổi Catalog event contract, chuẩn hóa tại `platform/event-contracts` theo migration tương thích. |
| `TD-006` | `scan-service` | MEDIUM | FT-038 đã có targeted recheck job nhưng chưa có GET status, idempotency enqueue, conditional update theo lease owner và chưa áp dụng Catalog existence filtering trong recheck. | Bổ sung status/idempotency, lease-fenced state update, rồi chạy Testcontainers với file rename/missing và existence classification. |
| `TD-007` | `scan-service` | MEDIUM | FT-039 bulk decision job chưa snapshot/cutoff tập candidate; projection được đánh giá lại theo từng batch nên candidate phát sinh giữa các batch có thể bị chọn. Chưa có GET status và bằng chứng concurrent decision/crash reclaim. | Chốt cutoff/generation khi enqueue, thêm status API, kiểm thử concurrent decision, lease reclaim, duplicate request và partial chunk. |
| `TD-008` | `scan-service` | LOW | Các worker job mới còn một số method/class vượt coding-style line length và chưa chạy formatter/compile trong session ưu tiên thông luồng. | Chạy formatter, compile và static analysis trước khi merge production. |

Chi tiết triển khai và bằng chứng trả nợ thuộc Feature Plan/commit tương ứng; file này chỉ giữ backlog còn mở.
