# Reference Capsule: BT-09C — Outbox Drain & Bounded Relay

> Trích xuất từ: `docs/reviews/2026-08-13-approve-5000-query-performance-assessment.md` (Section 2 & P1) & `2026-08-12-backend-quality-architecture-production-readiness.md` (TD-013).
> Phạm vi: Áp dụng cho Relay worker chuyển event từ outbox table sang Kafka.

---

## 1. Vấn đề của Outbox Relay cũ

- **`@Scheduled(fixedDelay = 5000)`**: Sau mỗi batch lại bắt buộc sleep 5 giây dù hàng đợi outbox đang có hàng trăm ngàn records chờ drain.
- **Publish tuần tự (Blocking I/O)**: Gửi từng message rồi block chờ Kafka acknowledgement (`join()` / `get()`) làm throughput bị nghẽn ở mức vài chục msg/s.
- **Lease Timeout**: Claim một batch quá lớn có nguy cơ giữ DB lock hoặc vượt quá thời hạn lease (30s) trước khi nhận đủ Kafka acks.

---

## 2. Thiết kế Continuous Drain & Bounded In-Flight

```mermaid
flowchart TD
    START(["Drain Loop Trigger"]) --> CLAIM["Claim Bounded Batch (1.000 events)<br/>SELECT ... FOR UPDATE SKIP LOCKED"]
    CLAIM --> CHECK_EMPTY{"Có event không?"}
    CHECK_EMPTY -->|Không| SLEEP["Backoff Sleep (1s)"] --> START
    CHECK_EMPTY -->|Có| ASYNC_SEND["Gửi bất đồng bộ tới Kafka<br/>(KafkaProducer.send với CompletableFuture)"]
    ASYNC_SEND --> WAIT_BATCH["Chờ Batch Acks với Timeout (ví dụ: 5s)"]
    WAIT_BATCH --> BULK_UPDATE[("Bulk update trạng thái PUBLISHED<br/>hoặc DELETE đã gửi thành công")]
    BULK_UPDATE --> CHECK_FULL{"Batch vừa rồi đầy (1.000 items)?"}
    CHECK_FULL -->|Đầy (còn backlog)| DRAIN_AGAIN["Tiếp tục vòng lặp ngay lập tức<br/>(Không sleep)"] --> CLAIM
    CHECK_FULL -->|Không đầy (hết backlog)| SLEEP
```

---

## 3. Các quy tắc điều phối bắt buộc

1. **Continuous Drain (Xả liên tục)**: Khi số lượng record claim được bằng đúng kích thước `batchSize`, lập tức kích hoạt chu kỳ claim tiếp theo mà không chờ fixed delay timer.
2. **Async Fan-Out + Bounded In-Flight**:
   - Sử dụng Kafka Producer bất đồng bộ và gom Future theo batch.
   - Giới hạn số lượng event đang bay trên mạng (In-Flight Buffer, ví dụ: tối đa 2.000 - 5.000 messages) để tránh tràn bộ nhớ socket/heap.
3. **Partition Key Consistency**: Bắt buộc gán partition key theo `subjectIdentity` (hoặc `rootKey`) để các event của cùng một aggregate luôn vào cùng một Kafka partition, đảm bảo thứ tự xử lý downstream.
4. **Lease Budget Protection**: Timeout cho toàn bộ quá trình gửi + ack một batch phải nhỏ hơn nhiều so với thời hạn lease (Timeout = 5s vs Lease = 30s).
