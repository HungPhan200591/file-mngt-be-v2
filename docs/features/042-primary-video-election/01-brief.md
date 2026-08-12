# FT-042 — Primary video election

Owner chính: `catalog-service`; producer: `scan-service`.

## Mục tiêu

Một subject có thể có nhiều file video. Catalog phải giữ tất cả video và bầu đúng một
`PRIMARY_VIDEO`: video đầu tiên làm primary; video không có tag được ưu tiên hơn video có tag.
Kết quả phải hội tụ độc lập với thứ tự discovery event.

## Acceptance criteria

- Scan phát video candidate với `role=VIDEO` và giữ `tagNames` của từng file.
- Video đầu tiên của subject được Catalog promote thành `PRIMARY_VIDEO`.
- Video không tag đến sau thay primary đang có tag; primary cũ hạ thành `VIDEO`.
- Video có tag đến sau không thay primary không tag.
- Cùng mức ưu tiên giữ primary hiện tại để tránh churn.
- Khi primary bị xóa, Catalog bầu lại từ các video còn lại.
- Subject `tagNames` phản ánh tags của primary hiện tại; Gallery tiếp tục đọc contract hiện hành.
- Event cũ gửi `role=PRIMARY_VIDEO` vẫn được chấp nhận.

## Ngoài phạm vi

- Không suy diễn `(Best)` là bản cắt hay bản gốc.
- Không thêm UI chọn primary thủ công trong feature này.
- Không chạy migration/import dữ liệu thật hoặc khởi động service.
