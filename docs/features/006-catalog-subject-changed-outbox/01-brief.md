# 006 Catalog subject changed outbox

Owner: `catalog-service`

## Vấn đề

Catalog đã nhận `media.file.discovered.v1` và ghi canonical subject/asset, nhưng thay đổi này chưa được phát tiếp thành business event. Vì vậy `query-service` và các consumer tương lai chưa có contract ổn định để dựng projection. Catalog cũng chưa có cách đọc nhanh outbox lỗi hoặc record đã bị chuyển vào DLT.

## Mục tiêu và acceptance criteria

- Mọi transaction thực sự tạo hoặc thay đổi canonical subject/asset đồng thời ghi một outbox event `media.subject.changed.v1` trong `catalog_db`.
- Event là snapshot đầy đủ của subject, có `subjectVersion`; delivery trùng hoặc sai thứ tự có thể được consumer nhận biết mà không cần thêm workflow status.
- Catalog publisher phát event theo partition key `subjectId`, chỉ đánh dấu `publishedAt` sau khi Kafka xác nhận và giữ lỗi để retry.
- Duplicate `media.file.discovered.v1` không làm đổi canonical data và không tạo thêm `media.subject.changed.v1`.
- Có API read-only, phân trang để xem Catalog outbox và DLT record; trạng thái outbox được suy ra từ timestamp/attempt thay vì persist enum status.
- Có integration test cho transaction canonical + outbox, no-op duplicate, publish retry và lưu DLT idempotent.

## Ngoài phạm vi

- Chưa triển khai `query-service` consumer, PostgreSQL/Elasticsearch projection hoặc Query API.
- Không có replay DLT, sửa payload, xóa outbox, dashboard UI, authentication/authorization hay distributed transaction.
- Không phát event lịch sử cho dữ liệu Catalog đã tồn tại; backfill/rebuild sẽ là feature riêng khi Query cần.
- Chưa hỗ trợ xóa subject/asset; khi có delete thật sẽ thiết kế contract tương ứng, không thêm cờ dự phòng lúc này.

## Câu hỏi/rủi ro mở

- Không còn quyết định nghiệp vụ chặn triển khai. `media.subject.changed.v1` dùng full snapshot và version tăng dần theo subject; consumer chỉ áp dụng version mới hơn.
- API vận hành hiện chỉ dành cho local/admin workflow. Security sẽ được chốt cùng Gateway, không mở rộng trong feature này.
