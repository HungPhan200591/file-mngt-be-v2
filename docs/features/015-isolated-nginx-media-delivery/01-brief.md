# 015 Nginx media delivery V2 tách biệt

Owner: `infra/compose`; `gateway-service` sở hữu CORS gọi business API từ browser.

## Vấn đề

ADR-005 hiện tái sử dụng container Nginx của V1 tại port `8888`. Điều này làm runtime V2 phụ thuộc vào repository/cấu hình V1, không thể chạy hay thay đổi độc lập và mâu thuẫn với nguyên tắc V1/V2 chạy song song không chung đụng.

## Mục tiêu và acceptance criteria

1. V2 có service Compose, tên container, file cấu hình Nginx và host port riêng; không mount config, container, frontend hay Compose của V1.
2. Nginx V2 dùng port `18119`, được bổ sung vào ADR-004 trước khi đưa vào Compose; port này không đang listen tại thời điểm triển khai.
3. Nginx V2 chỉ phục vụ static media `/files/G:/`, `/files/D:/`, `/files/E:/` từ bind mount read-only; không phục vụ UI hoặc proxy business API.
4. Gateway chỉ cho phép browser origin `http://localhost:18119` và `http://127.0.0.1:18119` gọi `/api/v2/**` ở local.
5. ADR-005 và Gateway HTTP contract chỉ rõ public base URL V2 riêng; V1 tại `8888` không bị sửa.

## Ngoài phạm vi

- Đổi logical locator `storageKey + relativePath`, Catalog data, event hoặc database.
- Authentication, TLS, thu hẹp approved media root, Nginx authorization hay migration gỡ Gateway legacy FT011.
- Khởi động Docker Compose, pull image, mount ổ đĩa thật hoặc chạy media runtime/E2E.

## Câu hỏi/rủi ro mở

- Mount toàn bộ `D:`, `E:`, `G:` chỉ chấp nhận cho local cá nhân như ADR-005; thu hẹp root là feature bảo mật riêng.
- Browser và frontend phải dùng `mediaUrl` V2 tại port `18119`; migration DTO/E2E direct delivery vẫn thuộc feature sau của roadmap ADR-005.
