# Hướng dẫn hiểu Backend V2

Đây là bộ tài liệu dành cho chủ dự án đọc trước khi tiếp tục FT013. Nó giải thích dự án theo ngôn ngữ gần với bài toán thực tế, không giả định người đọc đã quen microservice hay event-driven.

> Tài liệu này không phải source of truth và không được nạp mặc định cho AI Agent. Khi có khác biệt, ưu tiên [trạng thái dự án](../../../docs/STATUS.md), [kiến trúc](../../../docs/architecture/01-SUMMARY.md), contract và code hiện tại.

## Dự án giải quyết bài toán gì?

Các file video, ảnh và GIF đang nằm ở nhiều region/folder, nhưng UI cần nhìn chúng như những media có cấu trúc:

```text
Filesystem lộn xộn
    → nhận diện media
    → lưu dữ liệu chuẩn
    → tạo dữ liệu tối ưu để tìm kiếm
    → phát file cho frontend
```

Một câu tóm tắt:

> Scan tìm file; Catalog quyết định file thuộc media nào; Worker xử lý file; Query chuẩn bị dữ liệu để đọc; Gateway đưa API ra frontend.

## Trạng thái hiện tại

| Đã chạy thật | Chưa có hoặc mới ở kế hoạch |
| --- | --- |
| Scan preview, proposal, issue và approve/reject | Metadata kỹ thuật tự động từ Worker |
| Scan outbox → Kafka → Catalog | Thumbnail, GIF preview và file hash |
| Catalog lưu Subject/Asset canonical | Actress/Studio/Tag canonical đầy đủ |
| Catalog event → Query projection | Import/backfill toàn bộ dữ liệu V1 |
| Query PostgreSQL, Elasticsearch search, Redis detail cache | Observability ELK/OpenTelemetry hoàn chỉnh |
| Gateway, correlation ID và Media Delivery Range legacy | Gallery V2 hoàn chỉnh |

FT013 hiện mới có tài liệu `READY`, chưa có code. Media delivery mới dùng Nginx direct theo [ADR-005](../../../docs/adr/ADR-005-nginx-direct-media-delivery.md); Gateway Media Delivery là legacy FT011. Trạng thái mới nhất luôn xem tại [STATUS.md](../../../docs/STATUS.md).

## Thứ tự đọc đề xuất (System Primer Navigation)

1. **[01. Business Model](./01-business-model.md)**: Subject, Asset, JOKE, USE và Album là gì.
2. **[02. Kiến trúc Tổng quan](./02-architecture-overview.md)**: Tóm tắt 5 Microservices, Kafka, Redis, CQRS. *(Xem chi tiết SSOT tại [docs/architecture/01-SUMMARY.md](../../../docs/architecture/01-SUMMARY.md))*.
3. **[03. Use Cases & Data Flow](./03-use-cases-data-flow.md)**: Một tập tin đi qua hệ thống như thế nào.
4. **[04. Database Map](./04-database-map.md)**: Bản đồ sở hữu dữ liệu 3 databases (`scan_db`, `catalog_db`, `query_db`).
5. **[05. FT013 Primer](./05-ft013-primer.md)**: Nhập môn Media Worker Processing. *(Xem chi tiết tại [docs/features/013-media-worker-processing-foundation/](../../../docs/features/013-media-worker-processing-foundation/))*.
6. **[06. Observability Flow Overview](./06-observability-overview.md)**: Tổng quan luồng quan sát E2E từ Grafana & Kibana. *(Đọc deep-dive tại [manual/learning/deep-dive/observability/](../deep-dive/observability/))*.
7. **[07. API Flows Overview](./07-api-flows-overview.md)**: Bản đồ các REST API Flows chính. *(Đọc deep-dive Scan Service tại [manual/learning/deep-dive/scan-service/](../deep-dive/scan-service/), Outbox tại [manual/learning/deep-dive/transactional-outbox/](../deep-dive/transactional-outbox/), Virtual Threads tại [manual/learning/deep-dive/virtual-threads/](../deep-dive/virtual-threads/))*.

---

## Các Bộ Tài Liệu Deep-Dive Chuyên Sâu (Technical Deep-Dives)
Khi đã nắm bức tranh tổng quan ở System Primer, bạn có thể chuyển sang đọc các bộ Deep-Dive chi tiết:
- 🔍 **[Scan Service Deep-Dive](../deep-dive/scan-service/00-overview.md)**: Động cơ scan bất đồng bộ, Strategy Pattern parse filename & Proposal Approval.
- 📦 **[Transactional Outbox Deep-Dive](../deep-dive/transactional-outbox/00-overview.md)**: Giải quyết vấn nạn Dual-Write & Eventual Consistency.
- 🧵 **[Virtual Threads Deep-Dive](../deep-dive/virtual-threads/00-overview.md)**: Project Loom (JDK 25), Thread Pinning, Semaphore Throttling & Question Bank.
- 📊 **[Observability Deep-Dive](../deep-dive/observability/00-overview.md)**: Prometheus metrics, ELK structured logging, Correlation ID tracing & Dashboards.

## Sáu khái niệm cần nhớ trước

- **Subject**: media logic mà người dùng nhìn thấy, ví dụ video `START-001` hoặc một USE Album.
- **Asset**: file vật lý thuộc Subject, ví dụ MP4, JPG hoặc GIF.
- **Catalog**: nguồn dữ liệu chuẩn; nếu Catalog chưa biết thì hệ thống chưa coi dữ liệu đó là canonical.
- **Projection**: bản sao được tổ chức để đọc nhanh; Query có thể rebuild từ Catalog event.
- **Eventual consistency**: Catalog cập nhật trước, Query cập nhật sau một khoảng ngắn qua Kafka.
- **Idempotency**: nhận lại cùng request/event không được tạo thêm dữ liệu sai hoặc tác dụng phụ khác.

## Khi đọc thấy tài liệu và code khác nhau

Phân biệt ba mức:

1. `docs/architecture/`: kiến trúc mục tiêu và nguyên tắc dài hạn.
2. `docs/STATUS.md` cùng `docs/features/`: phần đã hoàn thành hoặc đang chuẩn bị.
3. `apps/*/src/`: hành vi thực tế đang chạy.

Ví dụ: kiến trúc nói Catalog quản lý Actress/Studio/Tag, nhưng database hiện tại mới triển khai Subject/Asset. Đây là roadmap chưa hoàn thành, không phải dữ liệu đã tồn tại.
