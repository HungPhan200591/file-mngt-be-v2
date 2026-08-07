# 026 Scan run liveness guard

Owner: `scan-service`

## Vấn đề

Lease hiện tại chỉ fence worker stale tại điểm commit và chỉ được thu hồi khi có
request scan mới hoặc worker đang treo trả control. Một câu SQL/COPY bị block có
thể giữ `scan_run` ở `RUNNING` vô hạn, khiến UI hiển thị sai trạng thái.

## Mục tiêu và acceptance criteria

- Mỗi `scan_run` mới có đúng một delayed deadline task theo `lease_until`; checkpoint
  commit thành công sẽ thay thế deadline cũ bằng deadline mới.
- Deadline chỉ có quyền fail run qua conditional update theo `runId`, `workerId`,
  `status = RUNNING` và `lease_until <= now()`; timer cũ hoặc chạy trễ không thể
  fail run còn lease hợp lệ.
- Khi deadline thắng, run thành `FAILED` và staging của run được dọn best-effort;
  worker cũ không thể commit sau đó nhờ lease fence hiện hữu.
- Query reconciliation bị giới hạn `statement_timeout = 30s`, COPY/mutation/finalize
  bị giới hạn `45s`, và mọi transaction scan dùng `lock_timeout = 5s` cục bộ.
- Timeout PostgreSQL không là cấu hình toàn hệ thống; dùng `SET LOCAL` trong đúng
  transaction scan để connection pool không bị rò cấu hình.
- Terminal run hủy delayed task còn lại; service restart vẫn dùng startup cleanup
  hiện hữu cho timer in-memory đã mất.

## Ngoài phạm vi

- Không thêm Redis, Kafka, distributed scheduler, polling/reaper định kỳ hoặc
  total-run deadline độc lập với progress.
- Không đổi REST API, Kafka event, schema/migration, semantics inventory hoặc lease
  duration 60 giây.

## Câu hỏi/rủi ro mở

- Timer là liveness trigger của process đang sở hữu run, không thay thế recovery sau
  process crash; startup cleanup và claim stale hiện hữu giữ vai trò safety net.
