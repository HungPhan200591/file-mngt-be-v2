# Core Reference — Durable Bounded Batch Pipeline

Tài liệu này rút ra kiến thức tái sử dụng từ
[SC-01](../../use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/summary/01-issues-and-solutions.md).
Đây là study reference, không phải source of truth của một feature hay dự án cụ thể.

## Bản chất trong một câu

> Khi một công việc có thể lớn, chạy lâu hoặc bị retry, hãy biến nó thành durable job xử lý từng
> work unit bị giới hạn, commit progress cùng kết quả, fence owner cũ và đo từng phase trước khi tối ưu.

## Khi nào phải nghĩ ngay đến pattern này?

Chỉ cần xuất hiện từ hai tín hiệu sau, hãy dừng thiết kế “một request + một vòng lặp”:

- Số item lớn hoặc không biết trước.
- Công việc chạy lâu hơn HTTP/request timeout hợp lý.
- Process có thể crash và phải chạy lại.
- Có nhiều worker hoặc scheduler cùng nhìn thấy việc.
- Mỗi item tạo database write, network call hoặc file I/O.
- Retry có thể tạo duplicate hoặc advance progress sai.
- Một transaction toàn bộ có rollback quá đắt.
- Cần theo dõi progress, pause, cancel hoặc recovery.

## Mental model dễ nhớ: D-B-F-I-S-T

```text
D — Durable progress
    Job, checkpoint và terminal state nằm trong durable storage.

B — Bounded resources
    Queue, batch, transaction, concurrency và timeout đều có giới hạn.

F — Fenced ownership
    Chỉ owner hiện tại được commit; worker cũ không thể ghi muộn.

I — Idempotent retry
    Chạy lại cùng work unit không tạo business effect trùng.

S — Set-based work
    Bulk data được xử lý theo set/batch, không thành N+1 hoặc hàng triệu entity.

T — Telemetry before tuning
    Đo phase, throughput, backlog, commit/rollback rồi mới chọn optimization.
```

Câu recall:

> **Việc phải bền, tài nguyên phải chặn, owner phải fence, retry phải idempotent, dữ liệu lớn phải
> theo set, tối ưu phải có telemetry.**

## High Level Design mặc định

```text
Trigger
  ↓ tạo durable job
PENDING → RUNNING(owner, lease, checkpoint)
  ↓
Bounded reader / producer
  ↓ backpressure
Bounded queue hoặc page
  ↓
Analyze / transform
  ↓
Chunk transaction
  ├─ business rows
  ├─ idempotency key
  └─ checkpoint + conditional fence
  ↓ lặp đến hết
COMPLETED | FAILED | CANCELLED
  ↓ nếu có boundary liên service
Transactional outbox → at-least-once delivery → consumer dedupe
```

Đừng bắt đầu HLD bằng Kafka, virtual thread hay batch size. Bắt đầu bằng **work unit, invariant,
transaction boundary và failure boundary**.

## Bảy câu hỏi thiết kế bắt buộc

1. **Work unit là gì?** Một file, một order, một media asset hay một page keyset?
2. **Progress nào là durable?** Checkpoint đại diện dữ liệu đã đọc hay dữ liệu đã commit?
3. **Ai có quyền commit?** Lease/fence nằm ở job, partition, tenant hay aggregate?
4. **Retry dựa vào key nào?** Business key, event ID hay `(jobId, itemId)`?
5. **Bulk boundary ở đâu?** Đọc, transform, DB mutation và external call có batch budget khác nhau không?
6. **Resource budget là gì?** Heap, connection, thread, I/O, transaction time và downstream rate.
7. **Bằng chứng nào quyết định tuning?** Phase latency, throughput, backlog age, execution plan hay memory.

Nếu chưa trả lời được bảy câu này, thêm worker chỉ làm failure xảy ra khó quan sát hơn.

## Sáu invariant portable

1. Checkpoint không được advance trước business data tương ứng.
2. Worker mất ownership không được commit dù đã xử lý xong trong memory.
3. Retry cùng work unit không tạo duplicate business effect.
4. Queue đầy phải tạo backpressure, không làm heap tăng không giới hạn.
5. Scratch state có thể mất; canonical state và handoff bắt buộc durable.
6. Terminal state phải có đường tới được khi worker chết, dependency timeout hoặc service restart.

## Ví dụ cụ thể — Import 10 triệu order từ file

### Bài toán ngây thơ

API nhận file rồi đọc toàn bộ, map thành entity và `saveAll()` trong một transaction. Khi process chết
ở item thứ 7 triệu, transaction rollback hoặc trạng thái nửa vời; retry có thể tạo order trùng. Heap,
persistence context và transaction log tăng theo input.

### Áp dụng D-B-F-I-S-T

#### D — Durable progress

Tạo `import_job(id, source_key, status, checkpoint, worker_id, lease_until, counters, last_error)`.
API trả `202 + jobId`; worker xử lý nền. Checkpoint chỉ đại diện chunk đã commit, không đại diện dòng
vừa đọc vào memory.

#### B — Bounded resources

- Stream file thay vì load toàn bộ.
- Queue và transform batch có capacity cố định.
- Mỗi transaction chỉ xử lý một chunk.
- Giới hạn số worker, DB connection và thời gian statement.
- Nếu downstream chậm, producer block/throttle thay vì tiếp tục ăn heap.

#### F — Fenced ownership

Worker claim job bằng conditional update. Mọi chunk commit mang `workerId` hoặc `generation`; update
checkpoint phải ảnh hưởng đúng một row. Nếu bằng zero, worker đã stale và toàn chunk rollback.

#### I — Idempotent retry

Dùng unique `external_order_id` hoặc `(source_key, source_row_id)`. Retry sau timeout có thể chạy lại
chunk, nhưng `INSERT ... ON CONFLICT`/conditional mutation không tạo order business trùng.

#### S — Set-based work

Stream row vào staging, validate theo batch, rồi `INSERT ... SELECT`/`MERGE` vào bảng canonical. Không
load 10 triệu JPA entity để kiểm tra từng row đã tồn tại. Error row được ghi theo batch với job/item key.

#### T — Telemetry before tuning

Đo riêng read/decode, validation, staging write, merge, commit và downstream handoff. Nếu merge chậm,
đọc execution plan; nếu decode chậm, mới xét parallel transform. Không tăng thread để che DB bottleneck.

### Transaction boundary

Trong một chunk:

```text
staging/canonical mutations
+ rejected-row evidence
+ counters
+ checkpoint conditional theo owner
= cùng commit hoặc cùng rollback
```

Nếu import tạo event downstream, ghi outbox cùng canonical order. Publisher có thể gửi lại; consumer
dedupe theo `eventId` hoặc business version.

### Failure drill phải hỏi

- Worker chết sau DB commit nhưng trước khi nhận response nội bộ thì retry ra sao?
- Lease hết hạn giữa merge thì stale worker bị chặn ở đâu?
- Một poison row làm fail cả chunk hay được quarantine theo policy nào?
- File nguồn thay đổi giữa hai lần retry được phát hiện bằng version/hash nào?
- Job treo do DB call không timeout được reaper đưa về terminal thế nào?

## Bốn ví dụ chuyển giao ngắn

### 1. Backfill thumbnail cho 5 triệu media asset

Work unit là `assetId`; job page theo keyset; concurrency bị giới hạn theo CPU/disk; output path mang
version/hash để retry idempotent; checkpoint commit sau khi metadata output durable. Worker stale không
được ghi completion. Đo decode, resize, storage write riêng trước khi tăng parallelism.

### 2. Rebuild Elasticsearch index

Đọc canonical DB theo keyset, bulk index vào shadow index, checkpoint page/version và retry theo document
ID. Không cho reader thấy index dựng nửa chừng; hoàn tất mới atomic alias swap. Nếu source đổi trong lúc
rebuild, dùng watermark/version và replay delta sau mốc đó.

### 3. Kafka replay để dựng read projection

Work unit là event/partition offset; consumer batch bounded; projection upsert idempotent theo `eventId`
và aggregate version. Offset/checkpoint chỉ commit sau projection transaction. Event cũ phải bị version
fence chặn, không overwrite state mới.

### 4. Dọn dữ liệu hết retention

Không `DELETE` hàng trăm triệu row trong một transaction. Job xóa theo keyset/time window bounded, lưu
checkpoint, throttle theo replication/IO budget và retry idempotent. Telemetry theo rows/s, lock time,
WAL/replica lag; chỉ tăng batch khi các budget vẫn an toàn.

## Domain transfer — Dự án LiveStream

Local project reference: `D:\Personal\live-stream-backend`.

Current evidence của project này chỉ gồm modular monolith, PostgreSQL, Redis session/stream state,
RTMP start/end webhook và HyperLogLog unique viewers. RabbitMQ mới có connectivity/test publish;
wallet ledger, gift flow, chat persistence và analytics pipeline chưa được implement. Các case dưới đây
là **transfer design**, không phải claim về code đang chạy.

Owner artifact để kiểm tra khi áp dụng thật:

- `live-stream-backend/README.md`
- `live-stream-backend/docs/architecture/system-context.md`
- `live-stream-backend/docs/contracts/business-flows.md`
- `live-stream-backend/docs/implementation/current-implementation-map.md`

### Case LiveStream chi tiết — Rebuild stream analytics theo ngày

Giả sử cần dựng lại report cho hàng triệu stream session/viewer event sau khi thay đổi công thức analytics.
Không chạy một request quét toàn lịch sử và update trực tiếp report đang phục vụ user.

| DB-FIST | Áp dụng vào LiveStream analytics |
| --- | --- |
| Durable | Tạo `analytics_rebuild_job` theo `dateRange + formulaVersion`; checkpoint theo keyset session/event |
| Bounded | Page source, aggregate partition và DB transaction đều bounded; giới hạn concurrency theo DB/Redis budget |
| Fenced | Chỉ generation hiện tại của một report window được publish; worker cũ không swap report |
| Idempotent | Unique theo `streamId + period + formulaVersion`; retry upsert cùng version không nhân đôi count |
| Set-based | Aggregate vào shadow/report table theo set; không load toàn bộ session thành entity graph |
| Telemetry | Đo source-read, aggregate, write, lag và mismatch; không tăng worker trước khi biết phase chậm |

Cutover chỉ diễn ra sau khi rebuild hoàn tất và đối soát. Nếu source thay đổi trong lúc rebuild, chốt
watermark rồi replay delta sau watermark. HyperLogLog hiện tại chỉ biểu diễn unique reach xấp xỉ; nó
không tự trở thành durable event history để rebuild mọi loại analytics.

### Ba case LiveStream ngắn

1. **RTMP webhook repair/replay:** lưu webhook vào durable inbox theo delivery/event key; worker xử lý
   batch bounded; state transition mang stream version; duplicate start/end idempotent; stale event không
   được đưa stream từ `ENDED` về `LIVE`.
2. **Gift settlement:** target flow cần sender debit và outbox cùng transaction; consumer credit streamer
   theo `giftTransactionId`; reconciliation job page các transaction `PENDING` và không credit hai lần.
   Baseline hiện chưa có durable wallet/ledger nên đây là thiết kế tương lai.
3. **Chat persistence backlog:** realtime broadcast không được giữ hàng triệu message trong memory;
   consumer persist theo batch bounded, dedupe `messageId`, checkpoint queue delivery và monitor backlog
   age. Chat/WebSocket business flow hiện chưa implement.

### Câu nối từ SC-01 sang LiveStream

```text
scanRun       → analyticsRebuildJob / settlementJob
rootKey       → streamId / reportWindow
file path     → sessionId / eventId / giftTransactionId
lease fence   → job generation / stream version
staging diff  → shadow report / reconciliation set
terminal swap → publish report / settle transaction
```

## Domain transfer — Tài chính

Workspace hiện không có repository Tài chính làm owner, nên phần này là **domain reference giả định**.
Khi có project thật phải thay các key, state, ledger rule, retention và regulatory requirement bằng
contract/evidence của dự án đó.

### Case Tài chính chi tiết — Import và đối soát settlement file

Ngân hàng/payment provider gửi file settlement lớn. Hệ thống cần import, đối chiếu với payment nội bộ,
phân loại matched/missing/amount mismatch và tạo adjustment được kiểm soát.

| DB-FIST | Áp dụng vào settlement |
| --- | --- |
| Durable | `settlement_job` giữ provider, business date, file hash/version, status và checkpoint đã commit |
| Bounded | Stream file, validate theo page/chunk, giới hạn DB transaction và downstream investigation queue |
| Fenced | Chỉ một generation xử lý cùng `provider + businessDate + fileVersion`; worker stale không finalize |
| Idempotent | Unique `providerTransactionId`; retry cùng file/chunk không tạo payment hoặc adjustment trùng |
| Set-based | COPY/staging rồi set-based join với payment/ledger; không query từng transaction kiểu N+1 |
| Telemetry | Đo parse reject, matched/mismatch, merge latency, backlog age và số tiền chưa đối soát |

Transaction chunk commit staging/result, error evidence, counters và checkpoint cùng nhau. Canonical
ledger không bị sửa/xóa để “khớp số”; correction nên là append-only adjustment/compensating entry theo
policy domain. Chỉ đánh dấu job `COMPLETED` khi tổng count/amount đã đối soát theo contract.

Failure drill quan trọng:

- Provider gửi lại cùng file với tên khác nhưng cùng hash thì dedupe ra sao?
- File cùng ngày bị replace bằng version mới thì generation cũ bị fence thế nào?
- Chunk commit thành công nhưng worker timeout thì retry dựa vào key nào?
- Tổng row khớp nhưng tổng tiền lệch thì job có được terminal thành công không?
- Adjustment đã post nhưng notification/outbound event chưa gửi thì outbox recovery thế nào?

### Ba case Tài chính ngắn

1. **End-of-day ledger reconciliation:** page account/ledger partition, compute debit-credit invariant,
   lưu discrepancy theo `(businessDate, accountId, ruleVersion)` và không mutate lịch sử để che mismatch.
2. **Interest/fee accrual:** durable job theo business date; một posting duy nhất cho
   `(accountId, accrualDate, ruleVersion)`; batch bounded và stale rule generation không được post muộn.
3. **Monthly statement generation:** keyset account, render/upload bounded, output key deterministic;
   retry không tạo statement version trùng; publish notification qua outbox sau khi statement durable.

### Câu nối từ SC-01 sang Tài chính

```text
scanRun       → settlementJob / reconciliationJob
rootKey       → provider + businessDate / ledger partition
relativePath  → providerTransactionId / accountId
fingerprint   → file hash + version / posting rule version
issue         → mismatch / missing payment / invalid amount
approval      → controlled adjustment / compensating posting
outbox        → settlement result / customer notification
```

Khác biệt cần nhớ: inventory/media có thể rebuild từ filesystem, còn financial ledger thường là
canonical audit history. Vì vậy scratch/staging có thể tái tạo, nhưng ledger entry, idempotency key và
adjustment audit không được đối xử như cache hoặc projection có thể xóa dựng lại tùy ý.

## Decision table

| Tình huống | Phản xạ mặc định | Vì sao |
| --- | --- | --- |
| Input nhỏ, hoàn tất nhanh, retry không nguy hiểm | Xử lý synchronous đơn giản | Durable job tạo chi phí vận hành không cần thiết |
| Input lớn/chưa biết trước | Stream + bounded buffer/page | Memory không tăng theo tổng input |
| Job dài và có restart | Durable job + checkpoint | Process memory không phải durable state |
| Nhiều worker thấy cùng job | Lease + conditional fence | Lock logic ở app không chặn stale commit |
| Bulk database mutation | Staging + set-based SQL/COPY | Tránh N+1 và persistence-context growth |
| Cross-service side effect | Transactional outbox + consumer dedupe | Không có distributed transaction miễn phí |
| Chưa biết bottleneck | Phase telemetry + baseline | Optimization theo trực giác dễ tối ưu nhầm boundary |

## Các kiểu “tối ưu” cần cảnh giác

- Tăng thread khi bottleneck là DB lock/I/O.
- Tăng batch nhưng vô tình giữ cả batch thành object graph trong heap.
- Thêm index cho read query lên bảng write-heavy mà chưa đo write amplification.
- Commit checkpoint ngoài transaction business data.
- Dùng `updated_at` như version/fence dù ordering không được bảo đảm.
- Cho rằng queue hoặc async tự động tạo durability và backpressure.
- Dùng scratch/cache làm source of truth.
- Giữ optimization không có A/B evidence vì “về lý thuyết phải nhanh hơn”.

## Câu trả lời phỏng vấn 30 giây

Khi gặp một batch job lớn, tôi dùng mental model D-B-F-I-S-T. Job và checkpoint phải durable; queue,
batch, transaction và concurrency phải bounded; ownership cần lease/fence để worker cũ không commit;
retry phải idempotent; bulk persistence ưu tiên set-based; và tuning chỉ làm sau phase telemetry. Tôi
commit checkpoint cùng business data, dùng outbox nếu có side effect liên service, rồi failure-test crash,
timeout, duplicate và stale worker. Pattern này áp dụng cho scan, import, backfill, projection rebuild hay
retention cleanup, không phụ thuộc framework.

## Active recall

1. Checkpoint đại diện “đã đọc” hay “đã commit”, và hậu quả nếu nhầm là gì?
2. Lease hết hạn nhưng worker vẫn đang chạy thì fence chặn commit ở đâu?
3. Queue bounded khác batch bounded thế nào?
4. Idempotency key nên gắn với request, job item hay business identity?
5. Khi nào staging được phép mất và khi nào bắt buộc durable?
6. Vì sao set-based SQL thường phù hợp bulk persistence hơn entity loop?
7. Async đã đủ để bảo vệ tài nguyên chưa?
8. Metric/trace nào chứng minh bottleneck nằm ở phase cần tối ưu?

## Case study và evidence

- [SC-01 issue/solution summary](../../use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/summary/01-issues-and-solutions.md)
- [SC-01 deep-dive](../../use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/01-deep-dive.md)
- [SC-01 question chain](../../use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/question-bank/01-question-chain.md)
- LiveStream local project: `D:\Personal\live-stream-backend` — current baseline phải đọc trước
  khi biến transfer design thành implementation claim.
- Tài chính: chưa có local project owner; hiện chỉ là hypothetical domain mapping.
