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
| Gateway, correlation ID và Media Delivery Range | Gallery V2 hoàn chỉnh |

FT013 hiện mới có tài liệu `READY`, chưa có code. Trạng thái mới nhất luôn xem tại [STATUS.md](../../../docs/STATUS.md).

## Thứ tự đọc đề xuất

1. [Business model](./01-business-model.md): Subject, Asset, JOKE, USE và Album là gì.
2. [Kiến trúc và kỹ thuật](./02-architecture-technical.md): năm service làm gì và vì sao cần Kafka/Redis/Elasticsearch.
3. [Use case và data flow](./03-use-cases-data-flow.md): một file đi qua hệ thống như thế nào.
4. [Database map](./04-database-map.md): mỗi database có bảng gì và dữ liệu nào là nguồn chuẩn.
5. [FT013 primer](./05-ft013-primer.md): chính xác feature tiếp theo sẽ bổ sung gì.

Không cần đọc toàn bộ source code trước. Sau năm chương trên, dùng phần “Đường đọc code” trong FT013 primer để lần theo một flow thật.

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
