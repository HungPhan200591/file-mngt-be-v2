# ADR-005: Nginx direct media delivery

## Status

Accepted — 2026-08-02

## Context

V1 đã phát ảnh/GIF/video trực tiếp qua Nginx tại `http://localhost:8888/files/<drive>:/...`. Nginx mount ổ đĩa read-only, dùng `alias` để map URL sang file thật và tự xử lý static file, byte range, cache validation.

FT011 từng thêm delivery qua `Gateway → Media Worker → filesystem` để xác minh asset locator V2. Đường này hoạt động nhưng không phù hợp làm đường phát file chính: Worker phải đọc Catalog cho mỗi request và Java nằm trên hot path của ảnh/video.

## Decision

- Nginx là media delivery plane duy nhất cho IMAGE, GIF và VIDEO.
- FE tải media trực tiếp từ Nginx; Gateway chỉ phục vụ business API (`Catalog`, `Scan`, `Query`).
- Catalog tiếp tục là source of truth cho locator `storageKey + relativePath`; tuyệt đối không lưu absolute path.
- Giữ URL public tương thích V1: `http://localhost:8888/files/<drive>:/<path-encoded>`. Nginx map `/files/G:/`, `/files/D:/`, `/files/E:/` sang các mount read-only bằng `alias`.
- V2 tạo `mediaUrl` từ locator và deployment root map đáng tin cậy rồi trả URL hoàn chỉnh cho FE. FE không ghép raw filesystem path; mapping `storageKey → local root + Nginx public prefix` chỉ nằm trong cấu hình triển khai.
- Tái sử dụng Nginx V1 `nginx_file_mngt` trên port `8888`; Backend V2 không tạo Nginx container hay host port mới.
- Scan và Media Worker dùng cùng logical `storageKey`, nhưng Worker chỉ đọc filesystem cho background processing: metadata kỹ thuật, thumbnail, GIF và hash.
- Không thêm authentication/proxy authorization ở giai đoạn local cá nhân. Nếu cần sau này, thêm `auth_request` hoặc `X-Accel-Redirect` trong feature riêng.

## Consequences

- Browser giữ hiệu năng static file/range của Nginx như V1; Media Worker không còn trên playback hot path.
- Không còn REST media-content public qua Gateway. `media-delivery-v1.yaml`, Gateway media route, controller/client delivery của FT011 trở thành legacy cần gỡ bằng feature migration riêng.
- Root map phải có một cấu hình triển khai thống nhất cho Nginx, Scan, Worker và URL resolver; không dùng absolute path trong Catalog hoặc event.
- Asset V2 thiếu `storageKey` vẫn không phát được; FE hiển thị unavailable state, không suy diễn path.
- Cấu hình V1 hiện mount toàn bộ ổ `D:`, `E:`, `G:` read-only. Điều này phù hợp môi trường local cá nhân hiện tại nhưng không phù hợp để expose qua mạng; nếu scope đổi, thu hẹp `alias` còn approved media roots trong feature riêng.

## Rollout và rollback

1. Thêm Nginx V2 root map và kiểm tra URL read-only/range cho fixture.
2. Bổ sung `mediaUrl` additive vào DTO read model. URL này do V2 tạo từ locator và root map, có dạng `/files/<drive>:/...` tương thích V1; chốt REST/event projection contract trong feature migration.
3. Chuyển Media Library V2 sang Nginx, thay E2E delivery.
4. Gỡ Gateway route và Media Worker content controller/client sau khi E2E mới pass.

Rollback trước bước 4 là đưa FE về URL content Gateway legacy; không đụng Catalog data hay V1.
