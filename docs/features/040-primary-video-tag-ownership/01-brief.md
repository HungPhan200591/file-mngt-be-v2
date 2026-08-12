# FT-040 — Primary video tag ownership

Owner: `catalog-service`; scope: `media.file.discovered.v2` materialization.

## Mục tiêu

Đảm bảo tag cấp tác phẩm chỉ được lấy từ asset có role `PRIMARY_VIDEO`. File ảnh,
GIF hoặc video phụ có thể được thêm vào subject nhưng không được xóa hoặc ghi đè
tag đã lấy từ primary video.

## Acceptance criteria

- Event `PRIMARY_VIDEO` có `tagNames` cập nhật tag cấp subject.
- Event asset phụ có `tagNames` rỗng hoặc khác cũng không xóa/ghi đè tag subject.
- Thứ tự nhận event không làm mất tag: primary trước asset phụ và asset phụ trước primary đều hội tụ đúng.
- Giữ nguyên event version, idempotency và asset materialization hiện tại.

## Ngoài phạm vi

Không thay đổi parser Scan, schema database, role asset, hoặc payload field của Kafka event.
