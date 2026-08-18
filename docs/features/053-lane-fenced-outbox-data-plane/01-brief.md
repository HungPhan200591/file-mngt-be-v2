# FT-053 — Lane-Fenced Outbox Data Plane

Owner: `scan-service`

## Vấn đề

FT-052 đã bỏ wave barrier và fixed delay, nhưng phép đo cùng fixture chỉ tăng từ `3.800` lên
`5.387 records/s` ở 25.000 event; workload 1.000.000 event không hoàn tất trong phiên đo. Benchmark dùng
acknowledgement tức thì và payload `{}`, nên bottleneck đã quan sát nằm trong application/PostgreSQL path,
không phải bằng chứng Kafka chậm.

Hot path hiện tại vẫn phải:

1. `SELECT ... FOR UPDATE SKIP LOCKED` và hydrate toàn bộ JPA entity;
2. đổi lease trên từng entity rồi `saveAll()` cho mỗi claim 500 event;
3. dispatch và conditional `markPublishedBatch()` qua một DB lane;
4. gọi `countByPublishedAtIsNull()` trong outer benchmark loop.

Tăng `maxInFlight` hoặc `claimSize` đơn thuần chỉ phóng đại heap, transaction và bind-list risk; nó không
loại bỏ per-record lease write, JPA dirty checking hay single-lane persistence.

Evidence hiện tại: [FT-052 outbox baseline](../../../apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/06-ft052-legacy-outbox-wave-baseline.md).

## Mục tiêu và acceptance criteria

FT-053 cải tổ data plane của Scan outbox bằng **virtual relay lane có lease/fencing ở cấp lane**, native JDBC
projection và set-based acknowledgement. Mục tiêu bắt buộc là relay 1.000.000 event đạt tối thiểu
`30.000 records/s` trên local qualification profile đã cố định.

### Performance gate

- **Prefilled / immediate-ack profile:** 1.000.000 pending event hoàn tất trong `<= 33.334 ms`, throughput
  từng valid run `>= 30.000 records/s`.
- **Prefilled / real-Kafka profile:** 1.000.000 event có payload đại diện được broker acknowledge và
  conditional-mark trong `<= 33.334 ms`, throughput từng valid run `>= 30.000 records/s`.
- Mỗi profile có tối thiểu 3 clean runs sau warm-up, báo `min/median/max`; không gọi 3 run là P95/P99.
- 25k chỉ dùng calibration và A/B phase timing; không thay thế qualification 1M.
- Benchmark timing không gọi exact pending `count(*)` trong hot loop. Exact count chỉ dùng trước/sau timed
  section để kiểm tra correctness.
- Đo riêng lane acquire, native fetch, dispatch, broker ack, fenced mark, DB pool wait, WAL/IO, CPU, heap và
  lane skew; không tối ưu chỉ dựa trên elapsed tổng.
- Khi chạy chồng lấp FT-051, sustained relay rate phải lớn hơn hoặc bằng
  `p95OutboxCommitRate × 1,2`. Với baseline tham chiếu `32.511 records/s`, capacity target hiện tại xấp xỉ
  `39.000 records/s`; nếu chưa đạt thì pressure gate phải chứng minh backlog/oldest age bounded và ghi rõ
  đây chưa phải full BT-09C qualification.

### Correctness và reliability gate

- Decision và outbox vẫn commit atomic trong `scan_db`; không mất event.
- Giữ nguyên topic, payload, `eventId`, `partition_key`, trace headers và at-least-once delivery.
- Mỗi virtual lane chỉ có một owner hợp lệ tại một thời điểm; mọi mark/failure transition phải kiểm tra
  `laneId + owner + fenceToken + leaseUntil`.
- Crash sau broker ack nhưng trước DB mark có thể republish cùng `eventId`; Catalog dedupe khiến canonical
  business effect trùng bằng `0`.
- Lease expiry, owner takeover, broker outage, mark failure, shutdown/restart và lane skew đều có focused
  integration evidence.
- Queue/future/payload trong memory có hard bound; không tạo task hoặc preload theo toàn bộ 1M event.
- FT-052 được giữ bằng feature flag làm rollback path trong giai đoạn qualification; hai relay không được
  cùng active.

## Ngoài phạm vi

- Không đổi `media.file.discovered.v2`, `media.file.removed.v1`, Kafka topic hoặc consumer contract.
- Không triển khai Catalog coalescing, Query bulk projection, DLT replay end-to-end hoặc tuyên bố
  `QUERY_DB_READY` đạt SLO.
- Không chuyển sang Debezium/log-based CDC trong FT-053; đây là một lựa chọn kiến trúc khác với cost vận hành,
  bootstrap và recovery riêng.
- Không hứa exactly-once giữa PostgreSQL và Kafka.
- Không xóa ngay `lease_owner`/`lease_until` trên từng event; giữ chúng trong rollback/soak window và dọn bằng
  migration riêng sau qualification.

## Câu hỏi/rủi ro mở

- Partial expression index theo virtual lane có thể tạo write amplification hoặc lane skew; phải đo histogram
  64 lane và `EXPLAIN (ANALYZE, BUFFERS)` trước khi chốt index.
- Multiple physical workers có thể đẩy bottleneck sang PostgreSQL WAL, connection pool, Kafka producer buffer
  hoặc broker partition; concurrency phải chọn từ saturation curve, không chọn theo số core.
- Commit giữa nhiều approval shard không có global strict order. Cùng `partition_key` được route ổn định về
  một relay lane và Kafka partition, nhưng consumer vẫn phải giữ `eventId` dedupe/version guard cho replay và
  failover.
- Migration/index build trên backlog lớn cần lock budget và rollback drill; không chạy migration thật trong
  giai đoạn lập tài liệu.

