# 005 Scan approval outbox

Owner: `scan-service` (producer), `catalog-service` (consumer)

## Vấn đề

Scan Preview mới chỉ tạo proposal/issue tạm thời. Người dùng chưa thể quyết định proposal nào được đưa vào dữ liệu media canonical; Scan cũng chưa có transactional outbox để phát hiện file đáng tin cậy sang Catalog.

## Mục tiêu và acceptance criteria

- API Scan cho phép quyết định một proposal là `APPROVE` hoặc `REJECT`.
- `APPROVE` ghi quyết định và một outbox event `media.file.discovered.v1` trong cùng transaction của `scan_db`; filesystem không bị đổi.
- Publisher Scan phát event Kafka từ outbox; Catalog consumer dedupe bằng `eventId` và upsert canonical subject/asset vào `catalog_db`.
- Re-delivery event không tạo subject/asset trùng. Cùng một decision request lặp lại trả kết quả hiện có; decision trái ngược bị từ chối rõ ràng.
- Có Testcontainers integration cho approval/outbox và Catalog consumer; E2E fixture kiểm tra Scan approval API.

## Ngoài phạm vi

- Không rename/move/delete file, không tạo thumbnail/GIF/hash, không cập nhật Query/Elasticsearch.
- Không có UI review, batch approval, retry dashboard, DLT replay UI hay distributed transaction.
- Không sửa V1 hoặc quét thư viện media thật.

## Câu hỏi/rủi ro mở

- Không còn quyết định kiến trúc mở. Với asset xuất hiện trước video, Catalog tạo hoặc dùng subject theo cùng `region + subjectType + identityKey`, sau đó thêm asset; tránh retry/state phụ chỉ vì thứ tự scan.
