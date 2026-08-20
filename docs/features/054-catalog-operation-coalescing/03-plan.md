# FT-054 — Operation-Scoped Catalog Coalescing — Plan

Status: `QUALIFICATION FAILED`

Design: [02-design.md](./02-design.md)

Runbook: [04-runbook.md](./04-runbook.md)

Workstream: `SC-01 / BT-09D`

SLO budget: Catalog canonical `10s` + Catalog relay `2s`

## Execution capsule

- **Owner chính:** `apps/catalog-service/`, database `catalog_db`.
- **Owner hỗ trợ:** `apps/scan-service/` chỉ cho `APPROVAL_COMMITTED` transactional watermark;
  `platform/event-contracts/` cho shared event records.
- **Scope/files:** Scan operation completion/outbox migration; Catalog batch Kafka config/consumer; operation
  staging/ledger/lane migrations; native ingest/finalizer/canonical persistence; v2 snapshot/watermark outbox;
  native continuous Catalog relay; metrics/config; focused unit/integration/benchmark tests; V19 finalizer
  access-path optimization; phase timing diagnostics; contract/STATUS.
- **Must preserve:** service database ownership, approval/outbox atomicity, `eventId` dedupe, stable partition
  key, at-least-once delivery, primary election, asset tags, tombstone semantics, actress registry, trace headers,
  bounded memory và no distributed transaction.
- **Read on demand:** [Brief](./01-brief.md), [Design](./02-design.md),
  [Catalog architecture](../../architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md#9-catalog-canonical-write),
  [watermark](../../contracts/events/media.approval.watermark.v1.md),
  [discovery v2](../../contracts/events/media.file.discovered.v2.md),
  [subject v2](../../contracts/events/media.subject.changed.v2.md), `apps/catalog-service/CONTEXT.md`,
  `apps/scan-service/CONTEXT.md` và `docs/architecture/03-CODING_RULES.md` trước khi sửa Java.
- **Không làm:** Query projection/consumer v2, Redis/search, `QUERY_DB_READY`, mixed discovery/removal bulk
  throughput, migration thật, service start, Docker, benchmark hoặc commit/push khi chưa có quyền riêng.

## Nguyên tắc one-shot bắt buộc

1. Không tạo FT-055a/FT-056 chỉ để sửa throughput Catalog còn thiếu của BT-09D.
2. FT-054 chỉ `DONE` khi semantics, failure gate và 1M performance gate đều pass.
3. Trạng thái trung gian hợp lệ là `IMPLEMENTING` hoặc `QUALIFICATION FAILED`; không dùng
   `IMPLEMENTED — qualification pending` làm điểm kết thúc feature.
4. Nếu benchmark không đạt, tiếp tục phase profiling → sửa hot path → chạy lại trong chính Plan này; chỉ
   thay đổi architecture khi evidence chỉ ra constraint ban đầu sai.

## Bước triển khai

### [P0] Chốt shared contracts và Scan completion bridge

1. Thêm shared record `MediaApprovalWatermarkV1` và `MediaSubjectChangedV2` với validation required fields,
   immutable list và test serialization/deserialization. Stage 10 mang additive
   `expectedDiscoveryRecordCount`/`expectedRemovalRecordCount`; tổng phải bằng `expectedRecordCount`.
2. Tạo additive Scan migration kế tiếp:
   - cho `scan_outbox_event.proposal_id` nullable chỉ với operation control event;
   - check constraint data event phải có proposal, watermark phải có operation;
   - unique partial guard `(operation_id, batch_id)` cho `approval-watermark-10`;
   - index không làm regress FT-053 lane fetch.
3. Refactor completion SQL của single/sharded approval thành một local transaction:
   - equality check `scan_committed_record_count = expected_record_count`;
   - persist discovery/removal output counts theo bounded chunk, không count full outbox ở completion;
   - transition `APPROVAL_COMMITTED`;
   - insert đúng một watermark outbox stage 10 với `sourceBatchCount`.
4. Bổ sung Scan watermark factory/serializer; vẫn publish qua FT-053 relay, key `operationId`, không tạo
   publisher mới.
5. Focused test: single/sharded completion, duplicate finalizer, zero/mismatch count, transaction rollback và
   outbox uniqueness.

### [P1] Tạo Catalog operation/staging/lane schema

1. Tạo một additive Catalog migration kế tiếp gồm:
   - `catalog_approval_operation` với manifest/counter/status/timestamp/failure fields;
   - `catalog_discovery_stage` logged, primary key `event_id`, typed payload và source coordinate;
   - `catalog_operation_subject` unique `(operation_id, subject_key)`, stable lane và final outcome;
   - `catalog_operation_lane` 64 lane/operation với owner, lease, fence, cursor, counters;
   - `catalog_outbox_relay_lane` 64 output lane;
   - operation metadata/check/unique partial indexes cho `catalog_outbox_event`.
2. Dùng fixed stable hash `firstByte(md5(subjectKey)) & 63`; thêm golden-vector SQL/Java test.
3. Index chỉ phục vụ ingest conflict, operation equality, subject keyset và pending relay; không tạo index
   trùng leading columns. Ghi `EXPLAIN (ANALYZE, BUFFERS)`, build time, size và WAL cho fixture 1M.
4. Thêm retention metadata nhưng không purge staging trên critical path; purge chỉ sau terminal reconciliation.

### [P2] Thay record listener bằng bounded batch ingest

1. Tạo dedicated Spring Kafka batch container factory:
   - `setBatchListener(true)`;
   - `AckMode.BATCH`;
   - batch nhận `List<ConsumerRecord<String,String>>`;
   - consumer concurrency/`max.poll.records`/fetch bytes có config và validation;
   - `BatchListenerFailedException` chỉ rõ poison record cho retry/DLT.
2. Parse tuần tự `eventType`, key, operationId/batchId, source coordinate và payload byte envelope. Khi gặp
   poison record, persist valid prefix trước nó rồi mới ném `BatchListenerFailedException`; không để partial
   offset commit bỏ qua prefix chưa durable.
3. Chia Kafka poll thành internal slice bounded theo record + bytes; không submit unbounded virtual-thread task.
4. Implement native ingest store:
   - PostgreSQL `COPY` vào temp table trong transaction;
   - `INSERT ... ON CONFLICT(event_id) DO NOTHING RETURNING` vào durable stage;
   - tạo/update subject workset chỉ từ rows mới;
   - tăng received counter một lần mỗi operation/slice;
   - listener return chỉ sau local commit.
5. Tạo watermark consumer stage 10; manifest/data đến bất kỳ thứ tự vẫn upsert cùng operation ledger.
6. Equality transition `READY_TO_COALESCE` chỉ khi exact discovery count và DLT gate pass;
   `expectedRemovalRecordCount > 0`, overflow hoặc mismatch
   chuyển `BLOCKED` với failure code có cardinality thấp. Operation thiếu input quá deadline chuyển
   `BLOCKED/CATALOG_INPUT_MISSING`, kể cả poison payload không parse được operationId.
7. Feature flag mutual exclusion: operation batch listener và legacy record listener không cùng consume bulk
   operation. Single decision `operationId=null` tiếp tục legacy path.

### [P3] Implement operation-wide native canonical finalizer

1. Tạo 64 logical operation lane và bounded physical worker pool; claim/takeover/renew/checkpoint/release đều
   dùng owner + lease + monotonic fence token.
2. Khi operation ready, freeze workset, materialize expected count từng lane và claim subject page bằng keyset.
3. Implement reducer/persistence theo bounded chunk:
   - set-based resolve/create `media_subject` theo natural identity;
   - deterministic asset/tag merge và tombstone anti-join;
   - deterministic primary election;
   - set-based subject actress/tag replacement;
   - set-based new-actress insert và one-bump master-data version;
   - subject version tăng tối đa một lần nếu final aggregate đổi.
4. Không hydrate full JPA aggregate trong hot path. JPA path được giữ sau feature flag cho rollback/single event.
5. Trong cùng fenced chunk transaction:
   - commit canonical mutations;
   - dựng/validate final v2 JSON từ canonical rows;
   - insert unique final snapshot outbox;
   - mark subject outcomes và lane cursor/counters.
6. Payload > 900 KiB tạo durable `SUBJECT_SNAPSHOT_TOO_LARGE`, operation `BLOCKED`; không giữ byte array lớn
   sau transaction và không publish payload chắc chắn bị broker reject.
7. Final operation transaction kiểm tra exact input/subject/outbox/DLT counts, rồi insert stage-20 watermark
   cùng transition `CATALOG_COMMITTED`.

### [P4] Thay Catalog outbox bằng native continuous relay ngay trong FT-054

1. Tạo compact native outbox projection; không attach JPA entity, không write lease từng event.
2. Tạo 64 virtual relay lane theo partition key và physical workers bounded.
3. Implement continuous sliding window:
   - native fetch pending theo lane;
   - async dispatch ngoài DB transaction;
   - completion queue bounded;
   - fenced set-based mark success/failure;
   - refill ngay sau durable mark;
   - adaptive idle backoff khi lane rỗng.
4. Control-plane pending/oldest-age sample theo interval; không exact `count(*)` trong hot loop.
5. Breaker/pressure gate pause finalizer hoặc batch ingest khi broker/pool/backlog vượt hysteresis; không làm
   interactive Catalog API mất connection pool.
6. `CATALOG_OUTBOX_OPERATION_RELAY_ENABLED=true` phải tắt legacy fixed-delay publisher. Rollback chờ lane lease
   hết hạn rồi mới bật publisher cũ.

### [P5] Observability, failure handling và operation runbook

1. Thêm phase timers/counters/gauges được liệt kê trong Design; không dùng operationId/subject/path làm label.
2. DLT observer persist `operationId`, source coordinate, failure code và resolution state; unresolved DLT tăng
   operation counter và cấm completion.
3. Graceful shutdown: stop poll/acquire, hoàn tất hoặc rollback current transaction, flush bounded completions,
   để lease expiry reclaim phần còn lại.
4. Viết runbook enable/canary/rollback, backlog/lag/blocked-operation observation, replay prerequisite và
   staging retention. Không tự chạy migration/reset topic/data.

### [P6] Qualification và tuning — không tách feature

1. Tạo `CatalogOperationCoalescingBenchmarkTest` và shared clean synthetic fixture, `@Tag("benchmark")`:
   - 25k calibration;
   - 1M input / 100k subjects / 10 assets mỗi subject làm qualification chính;
   - 1M input / 1M subjects làm worst amplification;
   - 1M input / 1 hot subject làm hot-key/byte-boundary;
   - duplicate 10%, manifest-first/data-first và representative p50/p95 payload.
2. Chạy baseline legacy trước khi thêm candidate FT-054 bằng
   [`CatalogLegacyRecordProcessingBenchmarkTest`](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/legacy/CatalogLegacyRecordProcessingBenchmarkTest.java)
   trên đúng hai workload 25K và 1M; ghi kết quả vào
   [`01-ft054-legacy-catalog-record-baseline.md`](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/01-ft054-legacy-catalog-record-baseline.md).
3. Candidate benchmark phải log riêng fixture preparation, stage ingest, watermark build/persist và finalizer
   durable wait; khi không hội tụ phải log operation status, received/completed/snapshot counters và pending lanes.
   SQL-level phase attribution vẫn cần `EXPLAIN (ANALYZE, BUFFERS, WAL)` từ run manifest, không suy ra từ tổng elapsed.
4. Đo ingest, dedupe, workset, canonical merge, snapshot build, outbox insert, broker ack và mark riêng.
5. Tuning matrix trong cùng FT-054:
   - internal slice `500 / 2k / 5k` và byte cap `8 / 16 / 32 MiB`;
   - finalizer workers `1 / 2 / 4 / 8`;
   - subject page `500 / 2k / 5k`;
   - relay fetch/flush `500 / 2k / 5k` và in-flight theo producer memory budget.
6. Loại candidate vượt heap, transaction timeout, lease budget, DB pool, lock/WAL hoặc producer buffer.
7. Chọn cấu hình nhỏ nhất đạt toàn bộ gate; ghi hardware/config, SQL plan, min/median/max và saturation evidence
   vào result/dashboard. Không tăng timeout để che hot path.
8. Nếu chưa đạt, dùng phase evidence tối ưu tiếp native SQL/index/chunk/lane/serialization trong FT-054 rồi
   chạy lại 25k → 1M. Không mở feature throughput kế tiếp.

## Kiểm tra

Chỉ chạy khi người dùng cấp quyền test/build/benchmark.

### Unit/static

- Event v2/watermark round-trip, required field và immutable collection.
- Stable subject/relay lane golden vectors.
- Batch coalescer deterministic với `0`, `1`, full batch, duplicate và multiple poll.
- Primary election/tag/metadata parity với path hiện hành.
- Manifest-first/data-first equality; overflow/mismatch/mixed operation bị `BLOCKED`.
- Lease acquire/takeover/fence; owner cũ không checkpoint/finalize/mark được.
- Feature flag mutual exclusion; batch rỗng là no-op.
- Tên test đúng `*Test`/`*IT`, fixture sạch và file source dưới 500 dòng.

### PostgreSQL integration

- Migration pass trên empty DB và DB có backlog hiện hữu; lock/build budget được ghi.
- Temp COPY + durable stage + offset boundary atomic; duplicate không tăng count.
- Workset immutable sau equality gate; 64 lane không miss/duplicate subject.
- Set-based canonical merge parity cho new/existing subject, tags, primary election, tombstone và actresses.
- Một version/outbox mỗi changed subject; unchanged subject không emit.
- Crash/rollback ở từng boundary không tạo partial canonical/outbox/checkpoint.
- Oversized snapshot block operation; no OOM/invalid outbox.
- `CATALOG_COMMITTED` chỉ xuất hiện khi exact counters và DLT gate pass.

### Kafka/fault integration

- Batch listener, partial poison record, retry và DLT không block record lành vô hạn.
- Same-key ordering, duplicate delivery và redelivery sau crash giữ canonical effect đúng một lần.
- Manifest/data reorder hội tụ.
- Broker delay/outage giữ queue/in-flight bounded và pressure gate hoạt động.
- Catalog snapshot/watermark giữ topic/key/payload/trace contract; Query v2 consumer chưa thuộc test scope này.
- Native relay ack-before-mark tạo duplicate technical delivery nhưng cùng eventId.

### Capacity acceptance

| Gate | Điều kiện pass |
| --- | --- |
| Canonical 1M | `<= 10.000 ms`, `>= 100.000 input records/s` trên profile 100k subject × 10 asset |
| Real-Kafka relay | Toàn bộ expected snapshots broker-ack + durable-mark `<= 2.000 ms` |
| Full FT-054 | First Catalog receive → last snapshot durable-mark `<= 12.000 ms` |
| Amplification | Snapshot v2 = unique changed subjects; không có intermediate snapshot |
| Repeatability | Warm-up + tối thiểu 3 clean runs/profile, báo min/median/max |
| Correctness | Input count exact 1M, data loss 0, duplicate canonical effect 0, unresolved DLT 0 |
| Boundedness | Heap/pool/in-flight/transaction/staging trong hard budget; không OOM/timeout/unbounded backlog |
| Hot key | Hoàn tất trong bound hoặc durable `SUBJECT_SNAPSHOT_TOO_LARGE`; không treo/OOM |

Ba run chỉ là benchmark acceptance cho feature. SC-01 P95/P99 vẫn cần sample window theo SLO owner và BT-09G.

## Rollout và rollback

1. Deploy additive shared contract và schema khi flags tắt; legacy consumer/publisher vẫn owner duy nhất.
2. Deploy Scan watermark producer disabled, rồi enable sau khi Catalog control consumer sẵn sàng.
3. Shadow ingest có thể validate/stage nhưng không canonical-write/publish; không chạy hai business-effect path.
4. Study cutover: drain/reset local v1 topic/projection theo contract được cấp quyền, tắt legacy bulk path, bật
   operation batch path và native relay; không dual-publish v1/v2.
5. Canary một Catalog instance/concurrency thấp; tăng theo measured saturation curve.
6. Rollback:
   - pause Kafka batch listener;
   - stop new finalizer/relay acquire;
   - chờ transaction/completion grace và lane lease expiry;
   - disable FT-054 flags, enable legacy path;
   - giữ staging/ledger để reconciliation hoặc replay, không tự xóa;
   - reset/replay offset chỉ theo runbook và quyền riêng của người dùng.
7. Chỉ dọn legacy v1 path/staging retention bằng task sau soak; cleanup không nằm trong critical cutover.

## Tài liệu cần cập nhật

- [x] Tạo Brief/Design/Plan FT-054 với one-shot gate; hiện trạng qualification là `QUALIFICATION FAILED`.
- [x] Route `docs/STATUS.md` và SC-01 context từ BT-09C/FT-053 sang BT-09D/FT-054.
- [x] Sửa watermark sample `CATALOG_COMMITTED` thành `stageSequence=20`.
- [x] Cập nhật SC-01 architecture/catalog capsule để không route về poll-local in-memory coalescing.
- [x] Khi implementation bắt đầu: cập nhật shared event contracts (`MediaApprovalWatermarkV1`, `MediaSubjectChangedV2`).
- [x] Additive schema, Scan `APPROVAL_COMMITTED` watermark outbox bridge, operation batch ingest và equality gate đã được triển khai.
- [x] Source candidate cho native canonical page finalizer, lane lease/fence, one-final-snapshot v2, stage-20 watermark, DLT/input watchdog, output pressure gate và native continuous relay đã hoàn thiện.
- [x] V19 finalizer access-path optimization, failed-lane release, last-lane completion guard và phase timing/timeout diagnostics đã được thêm; chưa chạy migration.
- [x] Focused contract/lane/mutual-exclusion/finalizer-release test source, candidate `CatalogOperationCoalescingBenchmarkTest` và runbook đã được thêm.
- [ ] Còn mở: migration/Testcontainers, focused unit/integration, real-Kafka relay, fault/crash/reclaim và warm 25k/1M qualification. Không ghi claim pass trước evidence thật.
- [ ] Chỉ khi toàn bộ gate pass: chuyển Plan `DONE`, distill STATUS và ghi benchmark evidence thật.
- Không cần ADR mới: database ownership, eventual consistency và transactional outbox boundary không đổi.
