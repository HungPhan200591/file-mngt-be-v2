# FT-059 — Catalog Logical Shard Completion Contract

Owner: `scan-service` + `catalog-service` + `platform/event-contracts`

## Vấn đề

FT-058 đã sửa liveness của control plane và pass functional regression, nhưng combined workload 1M vẫn
không hoàn tất trong deadline 120 giây. Bốn reconciliation unit đầu tiên liên tục vượt statement timeout
20 giây; mỗi unit chứa khoảng 6.250 subjects trong một transaction nên rollback làm mất toàn bộ tiến trình
của unit và retry chạy lại cùng failure domain.

Global `APPROVAL_COMMITTED` hiện chỉ cho Catalog biết toàn operation đã đủ 1M records. Catalog vì vậy phải
đợi global barrier, seal toàn operation, rồi mới reconcile 16 coarse units. Kiến trúc này không tạo được
pipeline overlap giữa input ingest, canonical reconciliation, Catalog outbox relay và Query projection.

Evidence owner: [FT-058 benchmark report](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/05-ft058-reliability-hardening.md).

## Mục tiêu và acceptance criteria

- Chốt một logical completion shard theo canonical subject key; mọi discovery event của cùng subject trong
  một operation bắt buộc thuộc cùng shard.
- Logical shard độc lập với Kafka partition vật lý và độc lập với số worker. `completionShardCount` được cố
  định khi accept operation; candidate mặc định là `64`, worker concurrency vẫn bounded riêng.
- Thêm event additive `media.approval.shard.completed.v1` từ Scan sang Catalog. Scan chỉ ghi event này vào
  transactional outbox khi shard ledger đã commit đủ exact discovery count.
- Scan và Catalog dùng cùng routing algorithm versioned, cùng golden vectors; mismatch hoặc manifest conflict
  phải fail closed, không tự suy đoán lại shard.
- Catalog dedupe discovery trước khi tăng counter. Một shard chỉ seal khi completion event đã đến, unique input
  count bằng expected count và không có unresolved DLT của shard; marker đến trước data vẫn phải hội tụ đúng.
- Sau khi seal, Catalog reconcile từng shard bằng durable page/checkpoint nhỏ hơn unit FT-058. Mỗi page commit
  canonical mutation, final subject snapshot outbox và checkpoint trong cùng transaction.
- Shard đầu tiên có thể reconcile và relay output trong khi các shard khác vẫn ingest. Query được phép consume
  `media.subject.changed.v2` sớm; global `CATALOG_COMMITTED` vẫn chỉ phát sau khi mọi shard hoàn tất và toàn bộ
  output đã được broker ACK rồi durable mark.
- Retry/crash chỉ chạy lại page chưa commit; không chạy lại toàn shard hoặc toàn operation. Duplicate marker,
  duplicate discovery và ack-before-mark không tạo duplicate business effect.
- Qualification gate: ba measured run 1M liên tiếp hoàn tất từ first Catalog receive tới final broker ACK trong
  `<= 120s`, exact cardinality, zero unresolved DLT và bounded heap/pool/WAL/lock wait. `30K–40K/s` chỉ là stretch.

## Ngoài phạm vi

- Không dùng Kafka partition vật lý làm business completion boundary và không yêu cầu broker partition count
  trùng `completionShardCount`.
- Không physical-shard database, không tạo bảng business theo shard và không cho service đọc/ghi database khác.
- Không tăng statement timeout, retry budget hoặc worker count để cứu transaction shape 16-unit của FT-058.
- Không đổi payload `media.file.discovered.v2`, `media.subject.changed.v2` hoặc global
  `media.approval.watermark.v1`; feature thêm một event contract mới.
- Không triển khai Query bulk projection/`QUERY_DB_READY`; phần đó vẫn thuộc BT-09E sau khi Catalog qua gate.
- Chưa hỗ trợ mixed discovery/removal operation; contract v1 qualify discovery-only workload như FT-058.
- Không suy diễn local/Testcontainers benchmark thành production P95/P99 hoặc capacity commitment.

## Câu hỏi/rủi ro mở

- Skew theo subject key có thể làm một shard nặng hơn đáng kể; qualification phải báo max/p95 records và
  subjects mỗi shard, không chỉ average.
- Scan phải đổi shard selection từ hash `proposal.id` sang canonical subject routing bucket. Migration/index
  không được làm regression FT-051 hoặc preload 1M proposals.
- Master-data upsert và canonical subject lock vẫn là shared PostgreSQL resources giữa các shard; pressure gate
  phải giới hạn worker/page concurrency độc lập với `completionShardCount`.
- Candidate `64` và page size `250–500 subjects` là giả thuyết calibration, không phải capacity fact. Giá trị
  cuối phải được giữ trong run manifest và không đổi giữa ba qualification run.
