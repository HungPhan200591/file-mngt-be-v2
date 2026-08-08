# FT-033 — Scan review read model

Owner: `scan-service` / `scan_db`. Không có scope FE trong feature này; browser tiếp tục
gọi các Scan REST endpoint hiện có qua Gateway.

## Vấn đề

Review queue hiện suy diễn item hiện hành trực tiếp từ `scan_proposal`, `scan_issue`,
`scan_decision`, `scan_file_inventory` và lịch sử `scan_run`. Với root hàng triệu file,
query phải lặp anti-join để loại observation cũ hoặc file đã mất; filter `REJECTED`,
counter worklist và phân trang vì vậy chậm và cạnh tranh tài nguyên với Scan database.

## Mục tiêu và acceptance criteria

- Tách write model Scan khỏi read model review bằng projection thuộc chính
  `scan-service`; không tách PostgreSQL instance hay database owner trong pha này.
- Queue proposal, issue hiện hành và các counter theo `rootKey` đọc từ projection có một
  item hiện hành cho mỗi `rootKey + sourceRelativePath`, không còn anti-join lịch sử ở
  request path.
- Approval/reject/reopen cập nhật write model và projection trong cùng transaction;
  FE đọc lại sau commit phải thấy state mới ngay.
- Reconciliation scan chỉ tạo một yêu cầu projection nhỏ sau terminal commit; không thêm
  row/index write theo từng proposal/issue vào COPY chunk, không đổi lease/checkpoint hay
  latency hot path.
- Projection bắt kịp theo batch, idempotent và có thể replay/rebuild một root; trong lúc
  đồng bộ, API trả trạng thái đồng bộ rõ ràng thay vì số liệu giả.
- Contract REST giữ path và pagination hiện có. Bổ sung field trạng thái projection chỉ
  theo kiểu additive nếu FE cần hiển thị đồng bộ.

## Ngoài phạm vi

- Không tạo read replica, database/service `query-service` mới hoặc cross-database write.
- Không thay đổi parser, inventory reconciliation, lease, SSE progress, Catalog event hay
  semantics `APPROVE` bất biến.
- Không triển khai recheck từng issue; hạng mục đó vẫn là `TD-006`.

## Câu hỏi/rủi ro mở

- Cần chốt durable delta cho projector sau terminal: giữ một snapshot delta tối thiểu hay
  rebuild root set-based. `scan_inventory_diff_stage` là `UNLOGGED` scratch và bị dọn nên
  không được dùng làm nguồn replay.
- Cần benchmark fixture 1M file: thời gian catch-up, queue depth, lock contention và
  read-after-decision; quyết định SLA trước khi chọn polling job hay consumer outbox nội bộ.
