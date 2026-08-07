# FT-024 — Inventory Matcher (BT-03)

## Link BT
BT-03 trong [SC-01 Break Tasks](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md).

## Vấn đề

Hiện tại (BT-02), mỗi lần scan đều parse 100% file trên ổ đĩa, bất kể file đó đã được scan và không có gì thay đổi kể từ lần trước. Với 1 triệu file, điều này nghĩa là 1 triệu syscall parse + 1 triệu potential proposal mỗi lần scan lại.

## Acceptance Criteria

1. **Skip parse file không đổi**: Khi `fileSize` và `fileModifiedAt` của một file vật lý trùng khớp với bản ghi trong `scan_file_inventory`, hệ thống KHÔNG gọi parser và KHÔNG tạo proposal mới cho file đó. Chỉ cập nhật `last_seen_run_id`.

2. **Parse file mới hoặc thay đổi**: File chưa có trong inventory hoặc có `fileSize`/`fileModifiedAt` khác → parse và tạo proposal/issue bình thường.

3. **Đánh dấu MISSING có bảo vệ Lease**: Sau khi duyệt xong toàn bộ filesystem, những entry trong `scan_file_inventory` (cùng `root_key`) không được nhìn thấy trong run hiện tại (`last_seen_run_id != currentRunId`) phải được cập nhật `state = 'MISSING'`. Việc này phải được thực thi thông qua `finalizeRun` có kiểm tra Lease (`status == RUNNING && leaseUntil > now && workerId`) nguyên tử cùng lệnh `complete`.

4. **Tầng lọc Profile chuẩn hóa**: Tất cả các file vật lý trên đĩa đều được seed/upsert vào `scan_file_inventory`; tuy nhiên chỉ những file được phân loại `NEW_OR_CHANGED` **và được `ScanCandidateParser.supports(profile, path)` chấp nhận** mới được gửi sang `ScanFileAnalyzer`. File không hỗ trợ (ví dụ `.jpg` trong profile video) được lưu inventory dạng `PRESENT` nhưng không tạo proposal/issue.

5. **Không duplicate & Test matrix đầy đủ**: Kiểm thử tự động chứng minh đủ 4 kịch bản: file không đổi (skip parse), file thay đổi (parse lại), file bị xóa (`MISSING`), và file không thuộc profile (chỉ seed inventory).

6. **Không thay đổi REST API, không thay đổi schema DB**.

## Ngoài phạm vi

- Resume scan từ checkpoint path cụ thể (BT-03 vẫn walk lại toàn bộ root, chỉ skip parse).
- Xóa entry MISSING khỏi inventory.
- Thông báo/event khi file bị MISSING.
- Bất kỳ thay đổi nào ở catalog-service, query-service hoặc media-worker.
