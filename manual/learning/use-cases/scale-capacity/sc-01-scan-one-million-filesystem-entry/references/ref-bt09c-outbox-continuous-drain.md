# Reference Capsule: BT-09C — Outbox Continuous Drain

> Execution owner: [FT-052](../../../../../../docs/features/052-outbox-continuous-drain/03-plan.md).
> Architecture owner: [section 8 — Scan outbox và Kafka](../../../../../../docs/architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md#8-scan-outbox-và-kafka).

## Baseline source hiện tại

- `ScanOutboxPublisher` chạy `fixedDelay=50 ms`, claim tối đa 500 record theo `(created_at, id)`.
- Publisher đã gọi `publishAsync()` cho cả wave trước khi duyệt acknowledgement; bottleneck không còn là
  publish tuần tự thuần túy như review cũ.
- Refill vẫn chờ wave hiện tại được duyệt xong và method kết thúc; mỗi wave vẫn chịu scheduler delay.
- Application window, claim size và lease chưa được tách; lease 30 giây và send timeout 5 giây đang hard-code.
- Success được conditional bulk mark theo owner; failure mark từng record. Ack thành công nhưng DB mark lỗi có
  thể publish lại và phải được consumer dedupe bằng `eventId`.

## Thiết kế đã chốt cho FT-052

1. **Continuous refill**: khi backlog còn dữ liệu, completion giải phóng slot nào thì claim/refill slot đó;
   idle delay chỉ dùng khi queue rỗng, breaker mở hoặc time slice yield.
2. **Bounded application in-flight**: claim không vượt free slots; callback ghi vào completion queue bounded,
   không mở DB transaction trên Kafka callback thread.
3. **Lease budget**: producer delivery timeout + acknowledgement slack + conditional-mark budget + safety
   margin phải nhỏ hơn lease; invalid config fail fast.
4. **Producer ordering guard**: explicit idempotence, `acks=all`, retries dương,
   `max.in.flight.requests.per.connection <= 5`; giữ partition key và dispatch order `(createdAt, id)`.
5. **Backpressure**: oldest pending age, pending count, in-flight saturation và failure/ack latency dùng
   hysteresis để pause/resume bulk approval claim; interactive lane không bị chặn.
6. **At-least-once**: publish ngoài DB transaction, conditional mark theo owner, duplicate sau crash/reclaim là
   hợp lệ và downstream phải dedupe/version-guard.

## Capacity/evidence gate

- Relay chạy chồng lấp với FT-051; không đặt mục tiêu phi thực tế là drain một prefilled backlog 1M trong 4 giây.
- Baseline FT-051 khoảng 32.511 outbox record/s; candidate relay target là tối thiểu 39.000 record/s với 1,2
  headroom, cần benchmark xác nhận.
- Tail target là `<= 4s` từ final outbox commit tới final broker ack/conditional mark cho workload 1M.
- Bắt buộc đo prefilled và overlapped profile: p50/p95/p99 ack, publish rate, max pending/oldest age, in-flight,
  DB mark time, broker failure, lease reclaim, duplicate và graceful shutdown.
- Chưa có runtime evidence thì không kết luận đạt `SLI-03` hay production-ready.
