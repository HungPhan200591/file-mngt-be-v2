# FT-041 — Scan rerun overwrite

Owner: `scan-service`, `catalog-service`, FE V2 Scan.

## Mục tiêu

Cho phép người dùng rerun một root để phân tích lại toàn bộ file và tạo lại proposal,
kể cả file không đổi hoặc asset đã tồn tại trong Catalog. Khi proposal được approve,
Catalog cập nhật canonical metadata và không tạo asset trùng.

## Acceptance criteria

- Màn Scan có action `Rerun & ghi đè` cho root đang chọn.
- Request rerun đưa toàn bộ file hiện có qua parser, không bị inventory diff hoặc
  `EXACT_ASSET_EXISTS` loại bỏ.
- Rerun vẫn tạo proposal để review; không tự ghi Catalog trước approval.
- Approval tạo event mới; Catalog overwrite metadata theo authority hiện hành và
  giữ nguyên asset nếu locator đã tồn tại.
- Scan bình thường giữ nguyên changed-only behavior.

## Ngoài phạm vi

Không xóa lịch sử scan, không reset database, không tự approve và không sửa file trên filesystem.
