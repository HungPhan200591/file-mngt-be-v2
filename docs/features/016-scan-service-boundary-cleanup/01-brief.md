# 016 Scan service boundary cleanup

Owner: `scan-service`

## Vấn đề

`ScanService` đang đồng thời chứa logic ứng dụng, public result record và public nested exception. Các caller phải phụ thuộc vào tên lồng như `ScanService.SomeType`; riêng một exception chung có thể được dùng cho cả scan run và proposal. Ranh giới sở hữu type vì thế khó đọc và khó bảo trì dù hành vi hiện tại vẫn đúng.

## Mục tiêu và acceptance criteria

- Tách toàn bộ public result record và public nested exception khỏi `ScanService` thành type top-level có tên theo resource/ý nghĩa.
- Giữ `Parsed` private nếu nó chỉ phục vụ parse nội bộ của `ScanService`.
- Phân biệt exception cho scan run, proposal, root scan không hợp lệ và scan đang chạy khi các trường hợp đó đã tồn tại.
- Giữ nguyên Scan OpenAPI, response body, HTTP status, `ProblemDetail`, persistence, event, transaction và hành vi preview/approval.
- Không còn import dạng `ScanService.SomeType` trong source production hoặc test.

## Ngoài phạm vi

- Không đổi REST API, Kafka event, database schema, ownership hoặc parser behavior.
- Không đưa HTTP annotation/DTO vào application result và không tạo abstraction dùng một lần.
- Không tách parser strategy, thay đổi luồng scan hay thực hiện migration/backfill.

## Câu hỏi/rủi ro mở

- Không có quyết định nghiệp vụ hoặc kiến trúc mở. Cần kiểm tra toàn bộ caller và test để bảo toàn JSON/error contract hiện tại.
