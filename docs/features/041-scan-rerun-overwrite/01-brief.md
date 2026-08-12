# FT-041 — Scan rerun overwrite — Brief

Owner: `scan-service`, `catalog-service`, `query-service`, FE V2 Scan.

## Mục tiêu

Cho phép chạy lại toàn root, ghi đè canonical metadata và đối soát cả file đã biến mất mà không reset database.

## Acceptance criteria

- Rerun parse lại file unchanged/existing và tạo proposal như một lần đối soát đầy đủ.
- Màn Scan có action `Rerun & ghi đè` cho root đang chọn.
- Rerun giữ proposal khi Catalog trả `EXACT_ASSET_EXISTS`; approve ghi đè metadata nhưng không tạo asset trùng locator.
- File trước đây `PRESENT` nhưng nay không còn tạo proposal `DELETE_ASSET`.
- Normal scan không tạo proposal xóa và không đổi inventory sang `MISSING`.
- Chỉ approve proposal mới xóa canonical asset trong Catalog.
- Subject hết asset bị xóa; Query PostgreSQL, Redis và Elasticsearch cuối cùng không còn subject đó.
- Retry, duplicate và event đến đảo thứ tự không làm asset/subject cũ sống lại.

## Ngoài phạm vi

- Không xóa file vật lý.
- Không reset database và không bypass review gate.
