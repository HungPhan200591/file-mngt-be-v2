# FT-054 — Operation-Scoped Catalog Coalescing

Owner chính: `catalog-service`

Owner hỗ trợ: `scan-service` cho `APPROVAL_COMMITTED` watermark

> **Historical decision:** FT-054 one-shot đã `CLOSED — QUALIFICATION FAILED`. Các câu “trong chính FT-054” bên dưới mô tả candidate ban đầu và không còn là instruction active; BT-09D đã được phân rã thành FT-055 (D1) và các feature D2–D4 kế tiếp.

## Vấn đề

BT-09C/FT-053 có thể đưa lượng lớn `media.file.discovered.v2` vào Kafka, nhưng Catalog hiện vẫn xử lý
từng record bằng một listener, một transaction JPA và một lần load/mutate/flush aggregate. Mỗi mutation lại
có thể tạo một `media.subject.changed.v1` riêng. Với 1.000.000 input, hình dạng I/O hiện tại có thể tạo:

- gần 1.000.000 transaction và dedupe lookup riêng;
- nhiều lần lock/update cùng `media_subject` khi một subject có nhiều asset;
- nhiều lần tăng version và serialize snapshot trung gian của cùng subject;
- gần 1.000.000 outbox event downstream thay vì một final snapshot cho mỗi subject;
- minimum idle time lớn ở Catalog outbox vì publisher hiện claim tối đa 500 rồi chờ fixed delay giữa các lần drain.

Coalesce chỉ trong một Kafka poll không giải quyết đầy đủ vấn đề: cùng subject có thể xuất hiện trong nhiều
poll, trong nhiều `batchId`, và manifest hoàn tất có thể đến trước hoặc sau data topic. Catalog cần một
operation ledger durable để biết khi nào đã nhận đủ input trước khi materialize final state.

FT-044 đã chốt contract `media.approval.watermark.v1`, nhưng source hiện chưa phát
`APPROVAL_COMMITTED`. Nếu thiếu bridge này, Catalog không có equality gate đáng tin cậy để kết thúc BT-09D.

## Mục tiêu và acceptance criteria

Candidate FT-054 ban đầu được thiết kế để triển khai trọn vẹn BT-09D trong **một feature**: batch ingest → durable operation coalescing → native
canonical merge → one-final-snapshot outbox → bounded continuous relay → `CATALOG_COMMITTED`.

### Correctness gate

- Scan ghi `APPROVAL_COMMITTED` watermark vào transactional outbox trong cùng transaction chuyển operation
  sang trạng thái tương ứng.
- Catalog dedupe input theo `eventId`; duplicate Kafka delivery không tăng `receivedRecordCount` và không tạo
  duplicate asset/subject effect.
- Catalog chỉ bắt đầu finalization khi đã nhận manifest, `receivedRecordCount = expectedRecordCount` và
  `unresolvedDltCount = 0`; manifest/data đến lệch thứ tự vẫn hội tụ.
- Mỗi changed `(operationId, subjectId)` chỉ tăng canonical `subjectVersion` tối đa một lần và tạo đúng một
  `media.subject.changed.v2` final full snapshot; unchanged subject chỉ hoàn tất workset, không phát event rỗng.
- Primary-video election, asset tags, subject metadata, actress auto-create và locator tombstone giữ nguyên
  business semantics hiện hành nhưng được áp dụng deterministic theo source order.
- Canonical state, processed input, operation checkpoint, final snapshot outbox và stage counter chỉ commit
  trong local transaction của `catalog_db`; không ghi database của service khác.
- `CATALOG_COMMITTED` chỉ được tạo khi mọi affected subject đã hoàn tất, output count đúng bằng unique changed
  subject count và không có unresolved DLT.
- Crash/retry có thể lặp technical work nhưng canonical duplicate effect và duplicate final snapshot đều bằng 0.

### Performance gate

- Profile qualification chính: 1.000.000 representative discovery event, 100.000 subject × 10 asset,
  payload v2 thật; Catalog ingest + coalesce + canonical write + durable final outbox hoàn tất trong
  `<= 10.000 ms`, tương đương `>= 100.000 input records/s`.
- Catalog output relay phải broker-ack và durable-mark toàn bộ `expectedSubjectCount` trong `<= 2.000 ms`
  trên real-Kafka profile, hoặc đạt rate tương đương `expectedSubjectCount / 2s` khi cardinality profile đổi.
- Full FT-054 phase từ record đầu tiên được Catalog nhận tới toàn bộ snapshot được broker-ack hoàn tất trong
  `<= 12.000 ms` trên qualification profile.
- Có thêm profile 1M input / 1 hot subject và 1M input / 1M subjects để đo hot-key và worst amplification;
  snapshot vượt byte envelope phải chuyển operation sang `BLOCKED`, không OOM hoặc publish payload quá cỡ.
- Mỗi profile 1M có warm-up và tối thiểu 3 clean runs, báo `min/median/max`; đây là benchmark evidence,
  không tự gọi là P95/P99 SLO qualification.
- Heap, in-flight record, DB connection, transaction size, staging bytes và Kafka producer buffer đều có
  hard bound; không preload toàn operation vào Java heap.
- Output amplification chính xác bằng `unique changed subjects / unique input events`; không phát snapshot
  trung gian.

### Definition of done one-shot

- FT-054 không được chuyển `DONE` nếu chỉ đúng semantics nhưng chưa đạt performance/correctness gate 1M.
- Nếu baseline đầu tiên chưa đạt, candidate ban đầu giả định profiling và tối ưu native SQL, chunk, lane, index,
  producer window tiếp tục trong chính FT-054; quyết định này đã bị supersede sau qualification failure.
- Chỉ sau khi FT-054 đạt gate mới chuyển sang BT-09E/Query bulk projection.

## Ngoài phạm vi

- Không triển khai Query consumer/projection v2, Redis generation switch hoặc `QUERY_DB_READY`; đó là BT-09E.
- Không thay đổi business meaning của subject identity, asset locator hay primary-video election.
- Không hứa exactly-once giữa PostgreSQL và Kafka; delivery vẫn at-least-once + durable dedupe/version guard.
- `media.file.removed.v1` tiếp tục theo path hiện hành và không thuộc workload throughput của FT-054;
  qualification SC-01 của feature chỉ nhận discovery proposal. Stage-10 watermark mang riêng discovery/removal
  counts; mixed operation phải chuyển `BLOCKED/UNSUPPORTED_MIXED_CATALOG_OPERATION`, không chờ equality vô hạn.
- Không dùng Kafka Streams, Debezium, shared database hoặc distributed transaction.
- Full DLT replay runbook và cross-service chaos matrix vẫn thuộc BT-09F; FT-054 vẫn phải có DLT isolation,
  operation `BLOCKED` và replay-safe persistence tối thiểu để không sai BT-09D.

## Câu hỏi/rủi ro mở

Không còn câu hỏi kiến trúc chặn implementation. Các rủi ro sau được khóa bằng gate trong Plan:

- PostgreSQL staging/WAL hoặc index có thể trở thành saturation point; phải đo phase timing và resource curve.
- Một final full snapshot có thể vượt Kafka byte envelope; default hard limit là 900 KiB và failure phải durable.
- Same-key ordering phụ thuộc partition key ổn định; không đổi partition count khi còn backlog/operation active.
- `media.subject.changed.v2` không dual-publish v1; end-to-end cutover chỉ bật sau BT-09E, còn FT-054 được
  benchmark/canary bằng feature flag độc quyền.
