# 004 Scan preview

Owner: `scan-service`

## Mục tiêu

Đọc filesystem từ root đã cấu hình, chọn parser theo loại root và lưu kết quả parse để review. Đây là luồng read-only: không ghi Catalog, không publish Kafka và không sửa file.

## Acceptance criteria

- API nhận `rootKey`, tạo scan run nền và trả `202` cùng `scanId`.
- Root path chỉ lấy từ cấu hình local; không nhận absolute path tùy ý từ API.
- Parser profile hỗ trợ JOKE video/assets, USE video/assets và USE Album; ambiguity/unparseable tạo issue, không tự đoán.
- Kết quả nằm hoàn toàn trong `scan_db`: run, proposal, issue; có pagination để review.
- Chỉ có status run `RUNNING`, `COMPLETED`, `FAILED`.

## Ngoài phạm vi

- Không approve/reject, outbox/Kafka, Catalog call, rename/move/delete, hash/thumbnail/GIF processing hay import V1.
- Không tạo UI; API + E2E HTTP là đủ để review ở phase này.
