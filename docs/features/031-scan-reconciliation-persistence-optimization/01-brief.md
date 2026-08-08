# FT-031 — Tối ưu persistence reconciliation Scan 1M file

Owner: `scan-service`

## Vấn đề

Run `019fdfe7-c0e9-7009-abe1-7ad807aef9f7` đã áp dụng V13 bỏ hai FK COPY hot
path nhưng vẫn hoàn tất 1.000.000 file trong 41,18 giây, vượt SLO cold scan dưới
30 giây. Parallel analyzer chỉ tốn 4,283 giây cho 10 chunk; phần còn lại tập
trung ở persistence reconciliation theo chunk.

Log hiện chưa tách thời gian `UPDATE/INSERT` inventory, `COPY` proposal,
`COPY` issue, checkpoint và transaction commit thực tế. Cần tối ưu có bằng
chứng, tránh tiếp tục thay đổi constraint hay tăng chunk size theo cảm tính.

## Mục tiêu và acceptance criteria

- Tách FT-031 thành các bước độc lập, chỉ triển khai bước sau khi bước trước có
  evidence benchmark và gate an toàn đạt yêu cầu.
- **FT-031.1:** có structured timing per-chunk sau commit thật, không dùng
  `runId` làm metric label; log terminal có thể quy phần chậm vào từng write
  phase.
- **FT-031.2:** COPY proposal/issue ghi theo buffer thay vì cấp phát và gọi API
  cho từng row; CSV null/empty/quote/newline, transaction rollback và output
  nghiệp vụ không đổi.
- **FT-031.3:** cold root không thực hiện `UPDATE` hoặc anti-join inventory vô
  ích; warm path giữ nguyên semantics `NEW`/`CHANGED`/`REVIVED`/`MISSING`.
- **FT-031.4:** chọn `business-chunk-size` từ benchmark có kiểm soát, đồng thời
  bảo đảm mutation statement timeout và lease budget vẫn còn headroom.
- Giữ nguyên REST, Kafka, SSE, ownership `scan_db`, lease fence, checkpoint,
  idempotency unique constraint, hai FK retained `decision/outbox → proposal`
  và atomicity chunk.

## Ngoài phạm vi

- Bỏ thêm FK, bỏ unique constraint, dùng UNLOGGED cho business table, hoặc đổi
  source of truth PostgreSQL.
- Parallel DB commit, pipeline overlap, resume sau restart/lease handoff.
- Đổi schema, REST/Kafka/SSE contract hay logic parser/evidence.

## Câu hỏi/rủi ro mở

- FT-031.4 chỉ đổi default khi benchmark cho thấy lợi ích và chunk lớn nhất vẫn
  hoàn tất an toàn dưới `mutation-statement-timeout` và lease deadline.
- Nếu 31.2–31.4 vẫn không đạt SLO, pipeline overlap là một feature follow-up
  riêng, không được gộp vào FT-031.
