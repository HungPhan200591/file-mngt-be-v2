# FT-043 — Video Gallery và throughput event

Owner: `catalog-service`, `query-service`; producer: `scan-service`; consumer UI: FE V2 Gallery.

## Mục tiêu

- Pipeline approve → Catalog không xử lý tuần tự từng Kafka acknowledgement và không tạo poison event do tái sử dụng subject version.
- Gallery hiển thị từng video thuộc folder/root được chọn, không dùng subject làm đơn vị card.
- Tag thuộc video asset; subject chỉ gom các asset liên quan cho detail và thumbnail.

## Acceptance criteria

- Outbox publish một batch bất đồng bộ, nhưng ghi kết quả thành công/thất bại riêng từng record.
- Mọi thay đổi asset làm aggregate subject tiến version trước khi tạo Catalog outbox; không tái sử dụng version cũ.
- `GET /api/v2/query/videos` trả cả `PRIMARY_VIDEO` và `VIDEO` có `storageKey=rootKey`, phân trang và lọc server-side.
- Tag filter và tag trên card dùng tag của chính video.
- Thumbnail ưu tiên `IMAGE`, fallback `GIF` cùng subject.
- Detail vẫn trả tất cả file cùng subject theo `PRIMARY_VIDEO`, `VIDEO`, `IMAGE`, `GIF`.
- FE preview list chỉ bắt đầu scroll khi có nhiều hơn 12 item.

## Ngoài phạm vi

- Không replay DLT, chạy migration, build, test hoặc khởi động service trong task nếu chưa được cho phép.
- Không xóa endpoint `/api/v2/query/subjects` cũ.
