# 013 Media Worker processing foundation

Owner: `media-worker`, phối hợp `catalog-service` và `query-service`

## Vấn đề

Media Worker hiện chỉ phát nội dung file qua HTTP. Asset mới vào Catalog chưa tự tạo processing job, chưa có metadata kỹ thuật bền vững và chưa chứng minh được vòng Kafka `Catalog → Worker → Catalog → Query`. Nếu làm thumbnail/GIF/hash ngay, feature đầu tiên sẽ trộn quá nhiều công cụ và failure mode trước khi nền idempotency được kiểm chứng.

## Mục tiêu và acceptance criteria

- Khi Catalog thêm một asset mới có `storageKey`, cùng canonical transaction phải ghi outbox `media.processing.requested.v1` cho asset đó.
- Media Worker consume request, resolve file qua root registry/safe-path hiện hành và đọc metadata nền tảng: `contentLength`, `mediaType`, `sourceLastModifiedAt`.
- Worker publish `media.processing.completed.v1`; chỉ hoàn tất Kafka record sau khi broker xác nhận completion event.
- Catalog consume completion idempotent, cập nhật đúng asset và phát snapshot `media.subject.changed.v1` có metadata optional.
- Query áp dụng snapshot mới và trả metadata qua subject detail/list mà không truy cập Catalog database.
- Retry cùng request không tạo tác dụng phụ khác nhau; duplicate/stale completion là no-op ở Catalog.
- File/root/path lỗi được retry hữu hạn rồi chuyển `<source-topic>.DLT`; không tạo thêm business status chỉ để mô tả lỗi kỹ thuật.
- Có integration test cho producer/consumer/idempotency và E2E fixture chứng minh luồng Scan → Catalog → Worker → Catalog → Query.

## Ngoài phạm vi

- Không tạo thumbnail, GIF preview, hash, ffprobe duration/resolution hoặc artifact storage; các phần này thuộc feature kế tiếp.
- Không thêm database cho Media Worker, job dashboard, manual retry API hay scheduler quét lại toàn bộ asset.
- Không xử lý asset legacy/manual thiếu `storageKey`; Catalog không phát request cho các asset này.
- Không sửa filesystem, không nhận absolute path trong event/API và không triển khai frontend.

## Câu hỏi/rủi ro mở

- Không còn quyết định chặn triển khai. FT013 giữ Media Worker stateless theo ADR-001; metadata extraction là read-only và an toàn khi Kafka retry.
- MIME phải dùng resolver deterministic hiện có với fallback `application/octet-stream`; không phụ thuộc hoàn toàn vào kết quả khác nhau giữa các hệ điều hành.
