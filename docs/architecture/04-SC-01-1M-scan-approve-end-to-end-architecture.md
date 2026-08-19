# SC-01 — Kiến trúc end-to-end cho Scan 1M và Approve 1M

Status: **PROPOSAL — chuẩn bị triển khai, chưa phải production qualification**

Ngày: 2026-08-17  
Owner: Scan, Catalog, Query và platform event pipeline

Phân loại: đây là **kiến trúc chuẩn cấp cross-service** của workload SC-01, nên đặt tại
`docs/architecture/`. Study pack của SC-01 chỉ định tuyến tới tài liệu này; không tạo bản sao trong
`manual/`. Đặc tả implementation của từng lát vẫn thuộc feature tương ứng trong `docs/features/`.

## Quan hệ với SC-01 và BT-09

Tài liệu này là preparation/design baseline, không phải bằng chứng BT-09 đã hoàn tất và cũng không thay
thế Plan của từng feature:

| Phạm vi | Owner hiện hành | Trạng thái cần giữ |
| --- | --- | --- |
| BT-09A — operation contract và watermark | [FT-044](../features/044-approve-1m-operation-contract/01-brief.md) | `DONE` |
| BT-09B — scan decision/outbox chunking | [FT-051](../features/051-logical-approval-sharding/01-brief.md) | `IMPLEMENTED — shardCount=4 DEFAULT`; production qualification vẫn pending |
| BT-09C — Scan outbox relay | [FT-053](../features/053-lane-fenced-outbox-data-plane/01-brief.md) | `READY`; FT-052 implementation không đạt performance gate |
| BT-09D → BT-09G — Catalog, Query, failure evidence, scale ladder | [SC-01 break task](../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned) | `PLANNED` theo dependency map |

Không có xung đột khi viết architecture trước khi các lát BT-09 triển khai. Xung đột chỉ xảy ra nếu dùng
đề xuất ở đây để ghi đè contract/Plan hoặc tuyên bố SLO đã đạt. Khi code khác proposal (ví dụ JDBC batch
hiện tại thay vì COPY), phải cập nhật feature design/ADR và dùng BT-09G để qualification; benchmark cục bộ
không tự nâng trạng thái feature.

Tài liệu này thiết kế toàn bộ pipeline khi Scan xử lý và approve 1.000.000 records. Mục tiêu không phải tối đa hóa một con số throughput đơn lẻ, mà là chọn đúng tốc độ theo business:

- API phải tiếp nhận operation nhanh và durable.
- Scan phải commit decision và outbox trong thời gian chấp nhận được.
- Catalog phải tạo canonical subject/asset đúng, không trùng và có audit.
- Query phải sẵn sàng cho Gallery/Media Library trước khi UI tuyên bố đã đồng bộ.
- Elasticsearch và media processing có thể chậm hơn nếu không nằm trên business critical path.

## 1. Quyết định kiến trúc

Chọn mô hình **durable staged bulk pipeline**:

1. API tạo durable approval operation và trả 202 Accepted cùng operationId.
2. Scan cố định input snapshot/cutoff của một completed scan run.
3. Scan xử lý bounded chunks; preparation có thể song song, source-of-truth commit vẫn có lane giới hạn.
4. Decision và Scan outbox commit atomic theo chunk bằng PostgreSQL bulk path; không mở transaction cho toàn bộ 1M.
5. Outbox publisher drain độc lập, có lease, retry at-least-once và backpressure.
6. Kafka vận chuyển event theo partition key của subject identity; aggregate khác nhau chạy song song.
7. Catalog consume bounded batch, dedupe và coalesce nhiều file events thành final subject snapshots.
8. Query consume final snapshots bằng staging/COPY hoặc native bulk upsert.
9. Search chạy trên slow lane riêng; SEARCH_READY không chặn QUERY_DB_READY nếu business không yêu cầu.
10. Control plane theo dõi watermark theo stage và chỉ phát ready khi cardinality, DLT và version invariant đạt.

Không chọn:

- distributed transaction Scan → Catalog → Query → Elasticsearch;
- một transaction duy nhất chứa 1.000.000 records;
- tạo nhiều business tables chỉ vì muốn có shard;
- tăng số node vô hạn để che bottleneck PostgreSQL;
- coi Kafka publish thành công là Query đã sẵn sàng.

## 2. Boundary của benchmark hiện tại

Benchmark tại [ApprovalDecisionChunkingBenchmarkTest.java](../../apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/approval/ApprovalDecisionChunkingBenchmarkTest.java):

- seed 1.000.000 proposals trước thời điểm đo;
- đo từ accept đến batches.process;
- dùng 40 chunks x 25.000, `copyEnabled=true`, `preparationParallelism=4`; `jdbcBatchSize=500` chỉ là fallback;
- bật reWriteBatchedInserts=true;
- tắt Scan outbox publisher, review projection và worker phụ;
- assert decision/outbox đủ số lượng sau khi Scan commit.

Số đo lịch sử khoảng 121 giây chỉ là evidence cho **Scan approval persistence**. Bản FT-050 + V24 mới nhất đạt
64.086 ms (64.086 milliseconds, khoảng 64,1 giây; 15.604 records/s) với PostgreSQL `COPY` và preparation
parallelism 4. Các số đo này không bao gồm:

~~~text
Kafka publish
→ Catalog commit
→ Catalog outbox publish
→ Query commit
→ Redis cache generation
→ Elasticsearch
~~~

Không dùng số benchmark này để kết luận QUERY_DB_READY hoặc SEARCH_READY.

Source-of-truth liên quan:

- [Backend V2 summary](01-SUMMARY.md)
- [Backend V2 high-level plan](02-PLAN.md)
- [FT-044 — operation contract](../features/044-approve-1m-operation-contract/02-design.md)
- [FT-045 — scan decision/outbox chunking](../features/045-scan-decision-chunking/02-design.md)
- [005 Scan approval outbox](../features/005-scan-approval-outbox/02-design.md)
- [037 Outbox backlog capacity](../features/037-outbox-backlog-capacity/02-design.md)
- [037 scale and cloud rollout](../features/037-outbox-backlog-capacity/05-scale-and-cloud-rollout.md)
- [media.approval.watermark.v1](../contracts/events/media.approval.watermark.v1.md)
- [approve-to-Query performance assessment](../reviews/2026-08-13-approve-5000-query-performance-assessment.md)

## 3. Business SLA theo stage

Không đặt cùng một SLA cho mọi stage. Các con số dưới đây là **SLO candidate cần benchmark**, chưa phải production guarantee.

| Mốc | Business meaning | Candidate |
|---|---|---:|
| ACCEPTED | Operation đã durable, HTTP có thể trả về | p95 < 1 giây |
| APPROVAL_COMMITTED | Scan đã ghi đủ decision và discovery outbox | p95 < 60 giây, p99 < 120 giây |
| CATALOG_COMMITTED | Canonical subject/asset đã hội tụ | candidate 2–5 phút |
| QUERY_DB_READY | Gallery/Media Library đọc được Query DB | candidate 5 phút |
| SEARCH_READY | Full-text/fuzzy search đã cập nhật | slow lane, candidate 15 phút |
| MEDIA_READY | Thumbnail/GIF/hash/technical metadata đã xong | async theo media workload |

Quy tắc:

- User chỉ cần biết request được nhận: dừng tại ACCEPTED.
- User cần biết Scan đã commit: dùng APPROVAL_COMMITTED.
- Gallery cần dữ liệu: chờ QUERY_DB_READY.
- Search không phải điều kiện mở Gallery: không chờ SEARCH_READY.
- Thumbnail/GIF bắt buộc để render: thêm MEDIA_READY, không kéo mọi processing vào approval transaction.

## 4. Dòng chảy tổng thể

~~~text
Reviewer/API
    │
    ├── approve-all
    │       └── operation + cutoff + expected counts
    ▼
Scan control plane
    ├── operation/shard ledger
    ├── bounded approval workers
    └── decision + outbox local transaction
            │
            ▼
       Scan outbox
            │ claim lease / publish async / retry
            ▼
       Kafka discovery topic
            │ partition by subject identity
            ▼
       Catalog batch consumer
            │ dedupe + coalesce + aggregate-safe write
            ▼
       Catalog canonical DB
            │ final subject snapshot outbox
            ▼
       Kafka subject snapshot topic
            │
            ▼
       Query batch consumer
            │ staging/COPY + version guard
            ▼
       Query DB
            ├── Redis generation switch
            └── Search outbox → Elasticsearch slow lane
~~~

## 5. Control plane và watermark

scan_approval_operation là parent job. Mỗi service sở hữu stage state và database của chính mình; không query xuyên database hoặc ghi database của service khác.

Durable metadata:

- operationId, scanRunId, batchId;
- expectedRecordCount;
- expectedSubjectCount sau Catalog hội tụ;
- input cutoff/snapshot;
- counters theo stage;
- stageSequence;
- unresolvedDltCount;
- timestamp từ acceptedAt đến từng watermark;
- failure code và replay reference.

Watermark contract hiện hành [media.approval.watermark.v1](../contracts/events/media.approval.watermark.v1.md) đã có:

~~~text
ACCEPTED
APPROVAL_COMMITTED
CATALOG_COMMITTED
QUERY_DB_READY
SEARCH_READY
BLOCKED / FAILED / CANCELLED
~~~

Không tạo state song song có cùng ý nghĩa. QUERY_DB_READY không phụ thuộc SEARCH_READY trừ khi business explicitly yêu cầu.

## 6. Scan discovery 1M

Giữ các điểm mạnh hiện có:

- bounded filesystem queue;
- staging UNLOGGED và COPY;
- set-based diff;
- changed set thay vì đưa unchanged rows vào Java;
- analyze parallel có giới hạn;
- commit/checkpoint có lease fence;
- page/chunk bounded để giới hạn memory và rollback blast radius.

Discovery phải hoàn tất trước approve-all. Nếu muốn approve khi scan vẫn đang chạy, đó là workflow snapshot khác và phải thiết kế riêng.

## 7. Scan approval 1M

### Snapshot

Approve-all chỉ chạy trên completed run và cố định scanRunId, expectedRecordCount, cutoff của proposal set và shard/partition definition. Proposal mới sau cutoff không tự lọt vào operation.

### Preparation

Preparation có thể song song:

- đọc compact projection;
- parse evidence;
- dựng event DTO;
- serialize JSON;
- sinh event ID;
- bulk-validate DELETE_ASSET, tránh inventory lookup N+1.

Preparation chia thành virtual-thread partition bị chặn bởi `preparationParallelism`, không tạo một task cho
mỗi record. Queue phải bounded; không preload 1M payload vào memory. Khi accept operation, Scan lưu
`proposalCutoffId` (max proposal id hiện hữu) và mọi page đều áp dụng `proposal.id <= proposalCutoffId`.

### Commit

Mỗi bounded chunk:

~~~text
BEGIN
  assert lease/fence
  PostgreSQL bulk write (COPY mặc định, JDBC batch fallback)
  update cursor/counter/lease
COMMIT
~~~

Decision, outbox và checkpoint cùng transaction. Chunk lỗi rollback toàn chunk; retry không tạo business effect trùng.

### Những phần BT-09B đã apply

| Phần | Implementation hiện tại | Kết quả đã quan sát |
| --- | --- | --- |
| Bounded chunk | `chunkSize=25,000`, 40 chunks cho 1M | Giới hạn heap và rollback blast radius |
| CPU preparation | Virtual-thread partitions, `preparationParallelism=4` | Giảm thời gian chuẩn bị/serialize |
| DELETE validation | Một bulk inventory query/chunk | Loại bỏ N+1 lookup |
| Durable write | PostgreSQL `COPY` decision + outbox | `copyEnabled=true` là hot path |
| Fallback | JDBC batch `500` | Chỉ dùng khi `copy-enabled=false` |
| Cursor safety | Index `(scan_run_id, id)` + `proposal_cutoff_id` | Không nhận proposal sau accept |
| Atomicity | Decision + outbox + checkpoint trong `REQUIRES_NEW` | Giữ nguyên invariant durable approval |

Benchmark historical của FT-050 + V24: `64,086 ms`, `15,604 records/s` cho 1M records với một DB writer
(`shardCount=1`); không dùng số này thay cho shard-4 hoặc `QUERY_DB_READY`.

### Evidence logical shard 1M

| `shardCount` | `measuredMs` | `throughputPerSecond` | Trạng thái |
| ---: | ---: | ---: | --- |
| 1 | 71,475 | 13,991 | Pass; single-writer baseline |
| 2 | 40,643 | 24,604 | Pass; ~1.76x shard 1 |
| 4 | 30,759 | 32,511 | Pass; ~2.32x shard 1, candidate hiện tại |
| 8 | timeout | — | Fail; transaction timeout tại shard checkpoint |

Đây là bằng chứng Scan approval persistence trên PostgreSQL `18.0-alpine`, `COPY`,
`preparationParallelism=4`, chunk `25,000`. Kết quả xác nhận không phải cứ tăng worker là nhanh hơn:
`shardCount=4` đang là điểm cân bằng; shard 8 đã làm DB/transaction lane contention vượt ngưỡng. Không kéo dài
transaction timeout chỉ để biến lần chạy shard 8 thành pass.

### Shard policy

Shard là logical partition, không phải bốn business tables. Bảng scan_approval_operation_shard chỉ giữ range/partition, cursor, counter, lease và status; các row vẫn nằm trong scan_proposal, scan_decision, scan_outbox_event.

Hiện trạng triển khai:

- một DB writer;
- nhiều CPU preparation workers;
- shardCount=4 (runtime default hiện tại);
- đã có shard ledger và nhiều bounded shard workers; `shardCount=4` là runtime default theo benchmark hiện tại.
  `shardCount=8` không đạt do transaction timeout; không tăng thêm nếu chưa có WAL/IOPS/lock/pool evidence.

Nếu nhiều shard cùng operation, chia theo aggregate identity hoặc range có snapshot. Không chia theo proposal_id nếu downstream không xử lý được event out-of-order của cùng subject.

## 8. Scan outbox và Kafka

FT-052 giữ lease trên từng event và đã triển khai continuous refill, nhưng runtime evidence chỉ đạt
`5.387 records/s` ở 25k; workload 1M không hoàn tất trong phiên đo. Target BT-09C hiện hành thuộc
[FT-053](../features/053-lane-fenced-outbox-data-plane/02-design.md):

~~~text
claim virtual lane bằng owner + fence token
→ native read pending event của lane, không ghi lease từng row
→ publish Kafka ngoài transaction bằng bounded lane workers
→ conditional batch mark theo lane owner + fence token
~~~

Đây là at-least-once có chủ ý. Consumer dedupe theo event ID; không coi broker ack cộng DB mark là exactly-once.
Hard floor qualification là 1M event `>= 30.000 records/s` (`<= 33.334 ms`) trên isolated và real-Kafka
profile. Khi relay chạy chồng lấp approval, capacity vẫn phải lớn hơn `p95OutboxCommitRate × 1,2` hoặc pressure
gate phải chứng minh backlog bounded; 25k không phải evidence thay thế 1M.

Metrics bắt buộc:

- pending count;
- oldest pending age;
- publish rate;
- Kafka producer in-flight;
- Kafka lag theo partition;
- retry/DLT.

Backpressure:

~~~text
outbox age tăng
→ giảm hoặc pause approval shard claim mới
→ giữ interactive lane
→ không làm backlog downstream tăng vô hạn
~~~

## 9. Catalog: canonical write

Catalog là owner duy nhất của subject, asset, actress, studio và tag.

Target flow được chốt chi tiết tại
[FT-054](../features/054-catalog-operation-coalescing/02-design.md). Coalesce phải bao phủ toàn operation,
không chỉ một Kafka poll:

~~~text
Kafka batch → bounded COPY/set-based dedupe vào durable staging
→ manifest + exact counter equality gate
→ freeze unique subject workset theo operation
→ 64 logical lane merge deterministic final state
→ native bulk canonical write + one version mỗi changed subject
→ một final subject snapshot v2 cho mỗi (operationId, subjectId)
→ native continuous Catalog outbox relay
→ CATALOG_COMMITTED khi exact subject/outbox/DLT gate pass
~~~

Durable staging cần thiết vì cùng subject có thể nằm ở nhiều poll/batchId và completion manifest có thể đến
trước hoặc sau data. Coalescing giảm aggregate load/save, outbox amplification và Query input mà không giữ
toàn bộ 1M event trong Java heap.

Trade-off:

- nhanh hơn và ít DB transaction hơn;
- thêm staging/WAL, operation ledger và deterministic merge logic;
- phải định nghĩa conflict/election deterministic;
- phải có version guard để event cũ không ghi đè state mới.

CATALOG_COMMITTED chỉ phát khi đủ unique discovery events, không còn unresolved DLT và mọi affected subject đã có final snapshot.

## 10. Query: read model

Query nhận final subject snapshots, không cần tái hiện toàn bộ Catalog transaction.

Target flow:

~~~text
Kafka batch
→ dedupe eventId
→ staging/COPY
→ native bulk upsert subject
→ bulk replace asset/tag collections
→ processed-event insert
→ query watermark + search outbox
COMMIT
~~~

Áp dụng version guard: incoming subjectVersion > stored projectionVersion.

Redis:

- cache-only;
- switch cache generation O(1) sau Query commit;
- Redis lỗi thì Query DB vẫn phục vụ;
- không DEL từng subject trên critical path.

QUERY_DB_READY cần đủ unique final snapshots, đúng projected count, không unresolved DLT và watermark durable.

## 11. Search và media processing

### Search

Search là derived index có thể rebuild. Nếu Query DB đã phục vụ Gallery/filter, Search chạy slow lane:

- search outbox ghi cùng projection;
- claim bounded batch;
- Bulk API theo byte-size;
- kiểm tra item-level errors;
- retry/backoff/DLT;
- SEARCH_READY riêng.

Elasticsearch Bulk API giảm overhead bằng nhiều action trong một request nhưng từng item vẫn có thể lỗi và request phải giới hạn kích thước. Xem [Elasticsearch Bulk API](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html).

### Media Worker

Thumbnail, GIF, hash và technical metadata dùng queue/resource pool riêng: CPU/I/O/GPU queue, quota riêng, retry theo file, output immutable/versioned. Không chặn Catalog; chỉ chặn Gallery nếu business thật sự cần artifact đó.

## 12. Backpressure và capacity

Pipeline capacity:

~~~text
min(
  Scan commit rate,
  Scan outbox publish rate,
  Catalog final-subject rate,
  Query projection rate,
  Search rate nếu Search nằm trong SLO
)
~~~

Không scale stage trước nếu stage sau không có capacity.

Priority lanes:

| Lane | Ví dụ | Chính sách |
|---|---|---|
| Interactive | preview, review page, single decision | priority cao |
| Bulk approval | approve-all 1M | bounded, pause được |
| Repair/replay | DLT/rebuild | priority thấp hơn |
| Search rebuild | rebuild index | ngoài critical path |
| Media processing | thumbnail/hash/GIF | queue theo resource |

Backpressure gates: Scan outbox oldest age, Catalog DB pool wait và transaction p95, Query write lag, Kafka consumer lag, Elasticsearch reject/index latency. Không dùng virtual thread không giới hạn để che connection pool saturation.

## 13. Failure model và consistency

| Failure | Hành vi |
|---|---|
| Worker chết trước commit | rollback, lease hết hạn, shard reclaim |
| Worker chết sau commit | cursor durable, không ghi lại effect |
| Kafka ack nhưng mark published lỗi | publish lại, consumer dedupe |
| Catalog poison event | cô lập, operation BLOCKED, replay sau repair |
| Query DB lỗi | Kafka giữ backlog, retry |
| Redis lỗi | bypass/fallback Query DB |
| Elasticsearch lỗi | search outbox retry, Query vẫn ready |
| Một shard lỗi | retry đúng shard, parent chưa ready |
| Undo approve | compensating operation/reopen, không xóa audit |

Invariants:

- một proposal tối đa một decision;
- approved proposal tối đa một business event effect;
- duplicate delivery không tạo canonical duplicate;
- event cũ không lùi version;
- unresolved DLT không được phát ready watermark;
- mỗi service chỉ ghi database owner của mình.

Không dùng distributed transaction. Dùng local atomicity, outbox, at-least-once, dedupe, version guard và replay.

## 14. Shard, node và database

Không tạo ngay scan_proposal_0, scan_proposal_1, scan_proposal_2, scan_proposal_3. Giữ logical tables và operation shard ledger. Worker lọc theo range/cursor hoặc partition assignment.

Scale ladder:

1. Một service node, một DB writer, nhiều CPU preparation workers.
2. Một node, 2–4 bounded shard workers nếu DB còn headroom.
3. Nhiều service replicas khi cần HA hoặc nhiều operation đồng thời.
4. Tách PostgreSQL owner/cluster khi DB contention hoặc blast radius yêu cầu.

PostgreSQL declarative partitioning là physical partitioning khác logical shard; chỉ dùng khi query pruning, archive hoặc write isolation được benchmark chứng minh cần thiết. Xem [PostgreSQL table partitioning](https://www.postgresql.org/docs/18/ddl-partitioning.html).

## 15. Trade-offs

| Quyết định | Giữ lại | Đánh đổi | Giá trị |
|---|---|---|---|
| Bounded chunk transaction | Atomic decision + outbox + checkpoint | Nhiều commit hơn | Retry/rollback nhỏ |
| Logical shard | Một schema/source of truth | Lease/coordination | Parallel có kiểm soát |
| COPY/set-based write | PostgreSQL durability | PostgreSQL coupling | Ít protocol overhead |
| Async outbox/Kafka | Local transaction | Eventual consistency | Các stage chạy độc lập |
| Catalog coalescing | Canonical ownership | Merge/version logic | Ít event amplification |
| Query bulk projection | Read model nhanh | Native SQL/staging | Gallery ready nhanh |
| Search async | Query độc lập | Search có thể lag | Không chặn business path |
| At-least-once + dedupe | Recovery/replay | Duplicate delivery | Không cần distributed exactly-once |
| Backpressure/priority | Bảo vệ interactive lane | Bulk job có thể chậm | Không sập vì burst |
| Stage watermarks | Completion trung thực | Nhiều metrics/state | Biết chính xác ready đến đâu |

## 16. Kế hoạch triển khai

### P0 — Contract và quan sát

- Giữ media.approval.watermark.v1.
- Chốt SLO theo stage.
- Đo timeline từ acceptedAt đến Query/Search.
- Thêm backlog, lag, WAL, IOPS, pool wait, batch p50/p95/p99, DLT.

### P1 — Scan approval

- Snapshot/cutoff.
- Shard ledger, mặc định một shard.
- Parallel preparation bounded.
- COPY decision/outbox cùng chunk transaction; JDBC batch chỉ là rollback/A-B fallback.
- Loại duplicate pending index sau query audit.
- Benchmark 1/2/4 workers; ban đầu một DB writer.

### P2 — Outbox

- Lease claim bounded.
- Async publish và conditional mark.
- Drain liên tục khi backlog có dữ liệu.
- Pause approval khi outbox age/downstream capacity vượt ngưỡng.
- Test crash/reclaim/duplicate.

### P3 — Catalog

- Kafka batch listener.
- Dedupe set-based.
- Coalesce theo subject identity.
- Bulk canonical upsert.
- Final subject snapshot và CATALOG_COMMITTED.

### P4 — Query

- Staging/COPY hoặc native bulk upsert.
- Version guard.
- Bulk processed-event.
- Redis generation switch.
- QUERY_DB_READY.

### P5 — Search/media

- Search Bulk API theo byte size.
- Item-level retry/DLT.
- SEARCH_READY riêng.
- Media queue theo resource và MEDIA_READY nếu cần.

## 17. Qualification matrix

| Profile | Mục đích |
|---|---|
| 1M proposals, ít subject | raw event/write capacity |
| 1M proposals, nhiều subject | Catalog coalescing |
| nhiều proposal cùng subject | hot key/election/order |
| payload nhỏ/lớn | CPU/network/WAL |
| Search chậm/lỗi | critical path độc lập |
| DLT/duplicate/reorder | correctness |
| worker/node restart | lease/replay |
| warm/cold DB/Kafka/cache | variance |

Correctness gate:

- decision count chính xác 1M;
- discovery outbox count chính xác;
- không duplicate subject/asset;
- không lùi version;
- replay khôi phục Query;
- Search lỗi không làm mất Query data.

Performance gate:

- ACCEPTED không phụ thuộc 1M;
- APPROVAL_COMMITTED đạt candidate p95/p99;
- QUERY_DB_READY đo từ acceptedAt;
- backlog không tăng vô hạn;
- backpressure bảo vệ interactive lane.

Operational gate:

- operation status và per-stage counters;
- dashboard backlog/lag/age/p95/p99/WAL/pool/DLT;
- replay/runbook;
- worker reclaim/failover test;
- production sizing không lấy từ loopback Testcontainers.

## 18. Near-domain evidence

Các nguồn chính thức dùng để kiểm tra pattern, không dùng làm proof SLO Backend V2:

- [Amazon S3 Batch Operations](https://docs.aws.amazon.com/AmazonS3/latest/userguide/batch-ops.html): manifest/job bất đồng bộ, task progress và completion report cho bulk object operations.
- [S3 Batch Operations job status and reports](https://docs.aws.amazon.com/AmazonS3/latest/userguide/batch-ops-job-status.html): terminal state và per-task failure details.
- [AWS Step Functions Distributed Map](https://docs.aws.amazon.com/step-functions/latest/dg/state-map-distributed.html): bounded MaxConcurrency, child workflows và tolerated failure threshold.
- [AWS Elemental MediaConvert queues](https://docs.aws.amazon.com/mediaconvert/latest/ug/working-with-on-demand-queues.html): queue capacity, priority và resource-constrained media processing.
- [PostgreSQL 18 populating a database](https://www.postgresql.org/docs/18/populate.html): COPY, transaction, index/FK/WAL trade-offs và ANALYZE.
- [PostgreSQL 18 table partitioning](https://www.postgresql.org/docs/18/ddl-partitioning.html): logical table với physical partitions và pruning.
- [Elasticsearch Bulk API](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html): bulk request giảm overhead nhưng item có lỗi riêng và request size cần bounded.

Chuyển giao được:

- durable job/control plane;
- bounded child work;
- per-stage/per-task completion;
- queue capacity và priority;
- retry/DLT/replay;
- bulk projection;
- derived index tách source of truth.

Không chuyển giao nguyên xi:

- cloud quota/throughput;
- topology production;
- failure threshold business;
- SLO 1M;
- semantics subject/asset của Backend V2.

## 19. Decision cuối cùng

Hệ thống không cần mọi stage chạy cùng tốc độ:

~~~text
Scan commit nhanh vừa đủ
→ Outbox/Kafka drain liên tục
→ Catalog coalesce và commit canonical đúng
→ Query bulk projection để Gallery ready
→ Search/media chạy slow lane
→ Watermark công bố chính xác stage nào đã sẵn sàng
~~~

Ưu tiên của thiết kế:

~~~text
correctness
→ audit/replay
→ bounded failure/recovery
→ business readiness
→ backpressure/stability
→ acceptable latency
→ peak throughput
~~~

ApprovalDecisionChunkingBenchmarkTest chỉ là test cho một phase. Qualification đúng phải đo toàn timeline từ acceptedAt đến các watermark và phải chứng minh cả correctness lẫn capacity.
