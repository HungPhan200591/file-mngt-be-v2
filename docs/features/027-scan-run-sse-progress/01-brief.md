# 027 Scan run SSE progress

Owner: `scan-service`, `gateway-service`

## Vấn đề

FE hiện gọi `GET /api/v2/scans/{scanId}` mỗi 3 giây. Polling tạo độ trễ hiển thị,
request thừa và đang trộn việc cập nhật trạng thái run với refresh proposal/issue.
Sau khi FT-025/FT-026 làm scan theo segment lớn, UI cũng cần tín hiệu tiến độ nhẹ
giữa các durable checkpoint mà không tăng tần suất ghi PostgreSQL.

Companion FE: `D:\Personal\file-management\v2\file-mngt-fe-v2\docs\features\004-scan-run-sse-progress\`.

## Mục tiêu và acceptance criteria

- Thêm additive endpoint `GET /api/v2/scans/{scanId}/events`, trả
  `text/event-stream` qua Gateway và direct Scan port.
- Stream gửi `scan.snapshot.v1` khi kết nối, `scan.progress.v1` tối đa một lần/giây
  khi có thay đổi và `scan.terminal.v1` khi run `COMPLETED`/`FAILED`, sau đó đóng.
- Payload phân biệt `observedFileCount` best-effort với `scannedFileCount` durable;
  PostgreSQL và `GET /api/v2/scans/{scanId}` vẫn là source of truth.
- Proposal/issue và decision vẫn dùng REST phân trang; SSE không stream item/file.
- Heartbeat comment tối đa mỗi 15 giây giữ đường truyền qua Gateway; một connection
  có lifetime tối đa 5 phút rồi client reconnect và nhận snapshot mới.
- Subscribe handshake không bỏ lỡ terminal transition giữa lúc đăng ký và snapshot;
  signal được queue đến khi snapshot đầu tiên đã gửi.
- Client disconnect, timeout, send error và terminal đều dọn subscription idempotent;
  giới hạn cấu hình mặc định 5 connection/run và 100 connection/process.
- Graceful shutdown dừng nhận stream mới, complete/remove mọi emitter và scheduler;
  không fail run, release lease hoặc thay đổi durable scan state.
- `404` trước khi response commit giữ ProblemDetail hiện tại; quá capacity trả `429`.
- Không thêm DB/Kafka/Redis cho SSE; signal hub process-local phù hợp target local một
  Scan Service instance và hạn chế này phải hiện rõ trong Design/rollout.
- Gateway không buffer stream, không cắt connection còn heartbeat vì timeout 30 giây,
  và giữ `X-Correlation-Id` trên response mở đầu.

## Ngoài phạm vi

- Không thay đổi state machine, lease, inventory, proposal/issue semantics hoặc schema DB.
- Không bảo đảm replay từng progress event, exactly-once delivery hoặc multi-instance
  fan-out; reconnect nhận snapshot hiện tại thay vì replay `Last-Event-ID`.
- Không thay polling bằng WebSocket, Kafka-to-browser hay Redis Pub/Sub.
- Không đổi UI layout, progress bar, filter, pagination hoặc decision behavior.
- Không code, build, test hay chạy service trong bước lập feature này.

## Câu hỏi/rủi ro mở

- Không còn quyết định chặn Plan. Khi triển khai phải chứng minh Gateway MVC flush
  từng SSE frame bằng integration test; nếu stack buffer response thì Plan quay lại
  `DRAFT`, không cho FE bypass Gateway như một workaround ngầm.
- Scale ngang Scan Service cần transport fan-out liên instance hoặc kiến trúc khác;
  không nằm trong target local hiện tại.
