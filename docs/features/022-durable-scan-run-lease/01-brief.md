# 022 Durable scan run lease (BT-01)

Owner: `scan-service`
Break task: [BT-01](../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-01--durable-scan-run)

## Vấn đề

Hiện tại, `scan-service` duyệt toàn bộ filesystem và lưu proposal/issue nhưng chỉ commit tiến độ khi đầy batch hoặc kết thúc run mà chưa có cơ chế khóa lease giữa các worker và lưu checkpoint bền vững theo chunk trên `scan_run`. Nếu worker bị tắt đột ngột hoặc có nhiều worker cùng chạy, không có cơ chế ngăn chặn claim trùng `rootKey` dựa trên lease timeout, cũng như không có thông tin checkpoint chunk và worker owner để khôi phục/truy vết tiến độ.

## Mục tiêu và acceptance criteria

- Bổ sung state kỹ thuật cho `scan_run`: `workerId`, `leaseUntil`, progress counters (`scannedFileCount`, `proposalCount`, `issueCount`), `checkpointChunk` và `checkpointAt`.
- Claim root theo lease: worker chỉ được chạy scan trên root nếu không có `scan_run` nào thuộc `rootKey` đó đang giữ lease còn hiệu lực (`leaseUntil > now()`).
- Nếu lease đã hết hạn (`leaseUntil <= now()`), run cũ được coi là stale/expired và worker mới có thể đánh dấu run cũ FAILED để claim root.
- Tách commit chunk độc lập: mỗi chunk (mặc định 500 items) được commit trong một transaction riêng (`REQUIRES_NEW`), đồng thời kiểm tra lại lease trước khi ghi, cập nhật progress counters, tăng `checkpointChunk` và gia hạn `leaseUntil`.
- Nếu worker bị mất lease (ví dụ bị kẹt/quá hạn), bước commit chunk tiếp theo phải hủy bỏ (abort).
- Thêm integration test cho BT-01:
  - Khóa lease: worker thứ hai không claim được cùng `rootKey` khi worker đầu tiên đang giữ lease.
  - Ghi nhận checkpoint chunk bền vững trong DB mà không làm mất dữ liệu đã commit khi worker ngắt giữa chừng.

## Ngoài phạm vi

- Chưa tạo bảng `scan_file_inventory` hay seed file inventory (dành cho BT-02).
- Chưa kiểm tra trùng lặp dựa trên inventory hay bỏ qua file không đổi (dành cho BT-03).
- Chưa tích hợp API batch existence của Catalog Service (dành cho BT-04/BT-05).
- Chưa thay đổi API response công khai ngoài việc bổ sung thông tin lease/checkpoint vào DTO nếu cần.

## Câu hỏi/rủi ro mở

- Không có quyết định kiến trúc mở. Thuật toán lease dựa vào DB timestamptz và transaction boundary độc lập cho từng chunk.
