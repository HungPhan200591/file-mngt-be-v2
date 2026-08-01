# 011 Frontend Gateway cutover

Owner: `file_mngt_FE` (frontend) phối hợp `gateway-service` và `query-service`

## Vấn đề

Gateway V2 đã có ingress ổn định tại `http://localhost:18100`, nhưng frontend hiện hành vẫn dùng API V1 tại `http://localhost:8081/api`. Không thể chỉ thay base URL vì Metadata Library gọi các endpoint owner-centric như `/actress/list`, `/studio/media`, trong khi V2 mới public subject-centric API `/api/v2/query/subjects`.

Query V2 cũng mới trả metadata và `relativePath`; chưa có HTTP contract để trình duyệt tải thumbnail, GIF hoặc nội dung media. Cutover trực tiếp màn hiện hành vì vậy sẽ làm mất nghiệp vụ và trải nghiệm đang hoạt động.

## Mục tiêu và acceptance criteria

- Chốt màn frontend đầu tiên dùng Gateway V2 mà không làm thay đổi hành vi của Gallery Web hoặc Metadata Library V1.
- Frontend chỉ biết một business API base URL V2 là Gateway `18100`; không gọi trực tiếp Query `18103` trong luồng bình thường.
- Dùng Query subjects để search, filter, order, phân trang và mở detail bằng UUID; không điều hướng bằng display name.
- Tạo Media Library V2 subject-centric mới; không sửa ngầm contract hoặc hành vi của Metadata Library hiện hành.
- Phát IMAGE/GIF/VIDEO qua media delivery V2 bằng `subjectId + assetId`; frontend không nhận hoặc gửi absolute filesystem path.
- Hỗ trợ GET/HEAD và HTTP Range để browser hiển thị ảnh/GIF và seek video; file chỉ được resolve trong root đã cấu hình.
- Giữ `X-Correlation-Id` từ response để hỗ trợ chẩn đoán lỗi, nhưng không hiển thị trong UI bình thường.
- Base URL được cấu hình theo runtime/environment; fallback direct Query chỉ là chế độ rollback chủ động, không tự động retry sang `18103` sau lỗi Gateway.
- Có test cho API client/mapping/error và một E2E browser hoặc HTTP scenario qua Gateway.
- V1 và V2 chạy song song; rollback không cần migration hay sửa dữ liệu.

## Ngoài phạm vi

- Thay thế toàn bộ Gallery Web hoặc Metadata Library trong một lượt.
- Owner-centric Actress/Studio API, chỉnh sửa metadata, scan/admin operation hoặc file operation.
- Authentication, upload, thumbnail/GIF generation mới và player inline tùy biến nâng cao.
- Tự động fallback từ Gateway sang direct service vì có thể che lỗi ingress và gửi lặp mutation trong feature sau.
- Import toàn bộ dữ liệu V1 sang V2.

## Câu hỏi/rủi ro mở

- Không còn quyết định chặn triển khai. Media Library V2 là màn mới; media delivery là contract V2 riêng do Media Worker sở hữu và chỉ public qua Gateway.
- Asset cũ chưa có `storageKey` không phát được nội dung cho đến khi được scan/import lại; API trả `404` mà không làm lộ cấu trúc filesystem.
- Media Worker thêm HTTP read boundary nhưng vẫn không có database và không sở hữu metadata canonical.
