# FT-032 — Scan review queue

Owner: `scan-service` (`scan_db`); FE consumer là module `scan` của FE V2
`D:\Personal\file-management\v2\file-mngt-fe-v2`, gọi Scan REST qua Gateway.

## Vấn đề

Incremental reconciliation chỉ tạo proposal cho file mới, thay đổi hoặc revived. Vì
vậy scan lại một folder không đổi sẽ không đưa proposal chưa duyệt của run cũ về
màn hình kết quả. Proposal đã `REJECT` cũng không được tự tạo lại, dù người dùng
có thể muốn xem xét nó lần nữa.

## Mục tiêu và acceptance criteria

- FE có một màn hình **Chờ duyệt** độc lập với lịch sử scan, mặc định hiển thị mọi
  proposal `PENDING` từ tất cả run và có thể lọc theo `rootKey`.
- Queue phân trang, trả đủ `scanId` và `rootKey` để FE duyệt một item mà không cần
  tìm lại run lịch sử.
- Sau terminal scan với `proposalCount = 0`, FE hiển thị liên kết tới queue; không
  chạy lại scan chỉ để tìm proposal cũ.
- `REJECT` nghĩa là bỏ qua tạm thời. Người dùng có thể reopen một proposal đã
  reject để đưa nó trở lại `PENDING`, không cần scan lại.
- Reopen không phát Kafka event, không sửa Catalog và idempotent: proposal đang
  `PENDING` cũng trả thành công; proposal `APPROVE` bị từ chối rõ ràng.
- `APPROVE` vẫn là quyết định cuối cùng; feature không hỗ trợ `APPROVE → PENDING`
  hoặc `APPROVE → REJECT`.
- Incremental reconciliation, inventory, SSE, lease/checkpoint và outbox approval
  hiện có không đổi.

## Ngoài phạm vi

- Không có `force`/full re-scan, parser reprocess, proposal dedupe xuyên run hay
  thay đổi hot path reconciliation.
- Không undo dữ liệu canonical ở Catalog, không có compensation Kafka event.
- Không đổi batch approve/reject hiện có và không tạo status/bảng nghiệp vụ mới.
- Không sửa source FE trong feature Backend này; FT FE companion là
  `D:\Personal\file-management\v2\file-mngt-fe-v2\docs\features\005-scan-review-queue`.

## Quy tắc nghiệp vụ đã chốt

| State hiển thị | Dữ liệu Scan | Hành vi |
| --- | --- | --- |
| `PENDING` | Không có `scan_decision` | Hiện trong queue mặc định; có thể approve hoặc reject. |
| `REJECTED` | `scan_decision.decision = REJECT` | Ẩn khỏi queue mặc định; hiện khi chọn filter Đã bỏ qua; có thể reopen. |
| `APPROVED` | `scan_decision.decision = APPROVE` | Đã tạo outbox discovery; không thể reopen trong feature này. |
