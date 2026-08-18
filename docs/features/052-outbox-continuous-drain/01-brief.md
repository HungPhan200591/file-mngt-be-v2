# FT-052 — SC-01 BT-09C: Outbox Continuous Drain

Owner: `scan-service`  
Dependencies: [FT-044](../044-approve-1m-operation-contract/03-plan.md),
[FT-051](../051-logical-approval-sharding/02-design.md)  
Architecture: [SC-01 end-to-end architecture — section 8](../../architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md#8-scan-outbox-và-kafka)  
Debt: `TD-013`

## 1. Mục tiêu

Tăng throughput relay từ `scan_outbox_event` sang Kafka sau khi BT-09B đã có thể tạo 1.000.000
decision/outbox trong khoảng 30–40 giây trên baseline local hiện tại:

- relay chạy chồng lấp với lúc approval còn đang commit outbox, không chờ đủ 1M record;
- khi còn backlog, refill slot publish ngay sau acknowledgement thay vì chờ hết một wave rồi nghỉ;
- giới hạn application in-flight, deadline và memory; không tạo task/future không giới hạn;
- lease chỉ được claim cho số slot có thể dispatch ngay và luôn đủ budget tới broker ack + DB mark;
- giữ at-least-once, partition key, conditional mark theo owner và consumer dedupe theo `eventId`;
- tạo backpressure có hysteresis để tạm dừng bulk approval claim mới trước khi backlog mất kiểm soát,
  nhưng không chặn interactive decision lane.

Mục tiêu stage là giữ publish rate ít nhất bằng peak outbox commit rate có headroom và tail-drain sau
final outbox commit nằm trong budget 4 giây của `SLI-03`. Đây là target cần benchmark, không phải tuyên bố
đã đạt.

## 2. Acceptance criteria

1. Khi query claim trả đủ work và pressure gate còn mở, coordinator tiếp tục claim/refill ngay; idle delay
   chỉ áp dụng khi không còn record claimable, breaker mở hoặc time slice kết thúc.
2. `maxInFlightEvents` là hard bound. Số record claim mới không vượt số slot trống; không có hàng đợi
   claimed-but-undispatched không giới hạn.
3. Completion được xử lý theo callback/queue bounded; một acknowledgement chậm không tạo barrier bắt buộc
   cho toàn bộ wave. Success được conditional bulk mark theo `(id, leaseOwner, publishedAt is null)`.
4. Producer giữ `enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection <= 5` và
   dispatch theo `(createdAt, id)`. `partitionKey` cùng event contract không đổi.
5. Terminal send failure/timeout ngừng intake mới theo breaker policy, ghi failure có owner fence và để
   retry/reclaim theo at-least-once. Ack thành công nhưng DB mark lỗi có thể publish lại; Catalog vẫn dedupe
   theo `eventId`.
6. Cấu hình fail fast nếu không thỏa lease budget:
   `producerDeliveryTimeout + acknowledgementSlack + markBudget + safetyMargin < leaseDuration`.
7. Backpressure dùng ít nhất oldest pending age, pending count, in-flight saturation và failure/ack signal;
   có high/low watermark để pause/resume bulk approval shard claim, không fail operation chỉ vì bị pause.
8. Graceful shutdown dừng claim mới, chờ bounded grace period để flush completion/mark; record còn lại được
   reclaim sau lease expiry, không bị coi là published khi chưa có broker ack.
9. Metrics có pending count/oldest age, claimed/published/failed rate, in-flight, broker ack latency,
   drain/tail latency, pressure state và owner-fence mismatch; không dùng path, identity hoặc event ID làm label.
10. Benchmark tách hai profile: prefilled backlog và overlapped approval→relay. Profile overlapped phải chứng
    minh relay không tụt lại lâu dài và tail-drain 1M target `<= 4s`; báo cả p50/p95/p99, không suy ra toàn bộ
    `QUERY_DB_READY`.
11. Không đổi REST, Kafka payload/topic, watermark, database ownership hoặc consumer contract.

## 3. Ngoài phạm vi

- Tối ưu BT-09B để decision/outbox đạt budget 5 giây; baseline 30–40 giây hiện tại vẫn là bottleneck riêng.
- Catalog batch/coalesce (`BT-09D`), Query bulk projection (`BT-09E`) và DLT replay end-to-end (`BT-09F`).
- Thêm Kafka partition hoặc tăng consumer concurrency khi chưa đo downstream capacity/lag.
- Tuyên bố đạt `SLI-03`, production sizing hay SLO percentile trước scale ladder và runtime evidence.

