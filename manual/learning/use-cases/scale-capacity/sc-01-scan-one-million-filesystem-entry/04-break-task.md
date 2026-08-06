# SC-01 — Break task triển khai

> Đây là danh sách lát triển khai, không phải ADLC Plan. Mỗi `BT` có thể mở thành một FT riêng khi bắt đầu làm. Chỉ code/test lát đang chọn; không tạo hoặc gọi phần ở các lát sau.

## Quy ước làm dần

- Mỗi BT phải chạy được với fixture nhỏ trước khi mở BT tiếp theo.
- Chưa đến BT nào thì không cần tạo class, API, migration hay config của BT đó; không cần comment code để giả lập phần chưa tồn tại.
- Data dev được phép reset khi BT cần đổi schema.
- Giữ Scan là owner `scan_db`; Catalog là owner `catalog_db`.

| Thứ tự | Break task | Owner | Kết quả dừng để test |
| --- | --- | --- | --- |
| BT-01 | Durable scan run + lease | Scan | Run có lease, progress/counter và checkpoint theo chunk; worker khác không claim cùng root. |
| BT-02 | File inventory seed | Scan | Full scan tạo/cập nhật `scan_file_inventory`; chưa thay đổi parser/proposal hiện tại. |
| BT-03 | Inventory matcher | Scan | Lần scan lại vẫn walk root nhưng bỏ parser/proposal cho path có `size + modifiedAt` không đổi; mark `MISSING` cuối run. |
| BT-04 | Catalog batch existence API | Catalog | Internal API nhận tối đa 500 candidate, trả classification locator/subject; chưa gọi từ Scan. |
| BT-05 | Scan–Catalog filtering | Scan + Catalog | Scan gửi đúng candidate mới/đổi theo batch; `EXACT_ASSET_EXISTS` không tạo proposal. |
| BT-06 | Keyset review | Scan | Proposal API dùng cursor `(source_relative_path, id)`; UI/HTTP test xem trang kế tiếp. |
| BT-07 | Bulk decision job | Scan | Approve/reject phạm vi lớn chạy job chunked, có progress; chưa tối ưu publisher. |
| BT-08 | Outbox backlog | Scan + Catalog | Publisher xử lý backlog chunked; Catalog vẫn dedupe event khi retry. |

## Chi tiết lát triển khai

### BT-01 — Durable scan run

- Thêm state kỹ thuật cho run: `workerId`, `leaseUntil`, progress counters và checkpoint boundary theo chunk.
- Claim root bằng lease; worker mất lease không được commit chunk tiếp theo. Tách worker để commit chunk độc lập; chưa có inventory hay gọi Catalog mới.
- Test: scan fixture nhỏ, worker thứ hai không claim được cùng `rootKey`; chủ động dừng worker sau chunk N rồi khởi động lại để xác nhận chunk đã commit không mất và worker mới khôi phục logical progress.
- Không yêu cầu resume `Files.walk()` chính xác từ path N ở BT-01; BT-03 mới dùng inventory để full walk lại mà không parse/gọi Catalog cho path đã không đổi.

### BT-02 — File inventory seed

- Thêm `scan_file_inventory(rootKey, relativePath, fileSize, modifiedAt, state, lastSeenRunId)`.
- Full scan hiện tại vẫn parse như cũ, đồng thời seed/upsert inventory theo chunk.
- Test: scan hai lần fixture nhỏ; kiểm tra inventory không duplicate theo `(rootKey, relativePath)`.

### BT-03 — Inventory matcher

- Trước parser, lấy inventory theo batch path và so `fileSize + modifiedAt`.
- Path không đổi: chỉ update `lastSeenRunId`; path mới/đổi mới parse.
- Sau full walk, entry không được thấy trong run thành `MISSING`.
- Test: thêm/sửa/xóa một file trong fixture; chỉ file thêm/sửa tạo proposal mới.

### BT-04 — Catalog batch existence API

- Chốt OpenAPI internal và Flyway/index Catalog cho locator `storageKey + relativePath`.
- API nhận tối đa 500 item, trả `EXACT_ASSET_EXISTS`, `EXISTING_SUBJECT_NEW_ASSET`, `NEW_SUBJECT`, `CONFLICT`.
- Test: gọi API trực tiếp với fixture Catalog; chưa thêm client Scan.

### BT-05 — Scan–Catalog filtering

- Thêm Scan client gọi BT-04 theo chunk candidate.
- Map classification: exact asset skip; các status còn lại tạo proposal phù hợp.
- Test: scan fixture chứa một locator đã có và một locator mới; chỉ locator mới xuất hiện trong review.

### BT-06 — Keyset review

- Thay proposal offset page bằng cursor và composite index `(scan_run_id, source_relative_path, id)`.
- Test: lấy trang đầu rồi dùng `nextCursor` lấy trang kế.

### BT-07 — Bulk decision job

- Tạo job persisted; claim proposal theo chunk; mỗi chunk ghi decision + outbox + progress cùng transaction.
- Test: approve nhiều proposal fixture; lặp request không tạo decision/event trùng.

### BT-08 — Outbox backlog

- Giới hạn batch/concurrency publisher cho outbox sinh từ bulk job.
- Test: tạo backlog fixture, publish retry và xác nhận Catalog chỉ có một business effect mỗi `eventId`.

## Khi mở một FT từ break task

FT phải link tới BT tương ứng, chỉ lấy scope của đúng BT đó và nêu dependency đã hoàn tất. BT-04/BT-05 là boundary Scan–Catalog nên FT của chúng phải cập nhật contract trong `docs/contracts/`; các BT chỉ chạm Scan không cần mở rộng contract.

## Tham chiếu

- [Overview](./01-deep-dive.md)
- [Touchpoints](./02-architecture-touchpoints-and-flows.md)
- [Inventory và cross-service deduplication](./03-cross-service-deduplication.md)
