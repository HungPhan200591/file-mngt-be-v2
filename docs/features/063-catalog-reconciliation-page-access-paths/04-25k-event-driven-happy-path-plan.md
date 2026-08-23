# FT-063 — 25K Event-driven Happy Path Plan

Status: `READY_TO_IMPLEMENT`  
Owner: `catalog-service` operation control plane  
Baseline owner: [03-plan.md](./03-plan.md)

## Quyết định đã chốt

Ưu tiên hoàn tất workload 25K trước. Không tiếp tục thiết kế theo giả định 1M trong feature này vì 1M chưa đạt
feasibility gate. V28 indexes được giữ; thay đổi tiếp theo tập trung loại scheduler/polling khỏi happy path, không
đại tu data model hoặc reducer trước khi control-plane overhead được loại bỏ.

Mục tiêu kiến trúc:

```text
Happy path 25K
ingest/watermark/shard marker commit
→ signal operation ngay
→ seal + claim + reconcile theo operation
→ signal relay ngay
→ subject snapshot broker ACK
→ commit operation + signal final watermark ngay
→ CATALOG_COMMITTED

Recovery path
scheduler quét durable state
→ gọi lại đúng operation progress API ở trên
```

Scheduler vẫn tồn tại để recovery sau crash/mất in-memory signal; scheduler không còn là dependency để operation
bình thường hoàn tất.

## Execution capsule

- Scope Java: operation batch/watermark/shard-completion consumers, một operation progress coordinator mới,
  operation-scoped methods trong completion-shard/unit stores, finalizer/coordinator scheduler adapters và
  operation outbox relay wake-up.
- Scope test: targeted UT/IT, ordering/crash recovery IT và combined 25K benchmark hiện tại.
- Scope SQL: chỉ migration forward-only khi operation-scoped query cần index/DB function; không sửa V23–V28.
- Must preserve: một ingest slice, một completion shard, một reconciliation unit; V57/V59 fence/lease/retry;
  exact subject/asset/tag/actress cardinality; primary election; version monotonicity; unique outbox; broker ACK.
- Không đổi REST, Kafka payload, logical completion contract, database ownership hoặc transactional outbox.
- Read on demand: [FT-059 Plan](../059-catalog-logical-shard-completion/03-plan.md),
  [25K evidence](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/10-ft063-reconciliation-page-access-paths.md)
  và [TD-023](../../TECHNICAL_DEBT.md).

## Baseline và latency budget

Baseline local 25K đã khóa:

| Thành phần | Thời gian |
|---|---:|
| Pipeline tới final broker ACK | 7.765 ms |
| Ingest một slice | 1.010 ms |
| Finalizer acquire | 820 ms |
| Reconciliation unit | 2.386 ms |
| Complete operation | 1 ms |
| Chưa quy được vào data processing chính | khoảng 3.548 ms |

- **Target:** `pipelineToFinalAckMs <= 3.000`.
- **Acceptance ceiling:** `pipelineToFinalAckMs <= 4.000`, đồng thời correctness pass; khoảng 3–4 giây được nhận
  là stable 25K và phần còn lại để debt.
- Không dùng kết quả local này để tuyên bố 1M/production qualification.

## Kế hoạch triển khai một mạch

### P0 — Freeze baseline — `DONE`

- Giữ V28 và benchmark shape: 25.000 input → 2.500 subject, một slice, một shard, một unit.
- Không chạy benchmark 250K/1M và không tăng worker/concurrency.

### P1 — Tạo operation-scoped progress API — `READY`

1. Tạo `CatalogOperationProgressCoordinator.request(operationId)` với signal coalescing bounded theo operation.
2. Signal chỉ là wake-up hint; durable PostgreSQL state quyết định bước nào được chạy. Signal mất sau crash là an toàn.
3. Tách operation-scoped methods dùng lại cùng fence/lease hiện tại:
   - seal/complete đúng completion shard của `operationId`;
   - acquire/reconcile đúng unit của `operationId`;
   - begin committing đúng `operationId` khi snapshots đã broker-ack.
4. Refactor các scheduler hiện tại gọi chung API/methods trên; không giữ hai implementation business logic.
5. Mỗi progress request chạy vòng chuyển trạng thái bounded, dừng ngay khi phải chờ input, DB lock hoặc broker ACK;
   không busy-loop và không giữ transaction xuyên Kafka I/O.

Files dự kiến chạm:

- `CatalogCompletionShardCoordinator`, `CatalogCompletionShardStore`.
- `CatalogOperationFinalizer`, `CatalogOperationUnitStore`.
- Một `CatalogOperationProgressCoordinator` và progress signal type mới trong `application.operation`.

### P2 — Nối direct trigger sau transaction commit — `PENDING`

Phát progress signal **sau commit**, không phát khi dữ liệu còn uncommitted:

1. Sau mỗi operation ingest slice commit.
2. Sau approval watermark commit.
3. Sau completion-shard marker commit.
4. Duplicate/out-of-order signals phải idempotent; marker đến trước input hoặc input đến trước marker đều hội tụ.

Consumers/stores dự kiến chạm:

- `CatalogOperationBatchConsumer` / `CatalogOperationStageStore`.
- `CatalogApprovalWatermarkConsumer`.
- `CatalogApprovalShardCompletedConsumer` / `CatalogCompletionShardStore`.

### P3 — Event-driven outbox relay và ACK continuation — `PENDING`

1. Thêm `operationId` vào relay projection/result nội bộ để biết operation nào vừa durable-mark `published_at`.
2. Sau reconciliation commit, gọi non-blocking `requestDrain()` để relay chạy ngay, bỏ idle-backoff khỏi critical path.
3. Sau subject snapshot ACK được durable-mark, signal lại operation để thử `beginCommitting(operationId)` ngay.
4. Khi final watermark được tạo, wake relay ngay lần nữa; DB trigger hiện tại vẫn là owner chuyển
   `COMMITTING → CATALOG_COMMITTED` sau broker ACK.
5. Relay scheduler gọi cùng drain coordinator và chỉ là fallback. Dùng single-flight/coalescing để scheduler và
   direct wake không tạo hai drain wave cạnh tranh cùng lane.

Files dự kiến chạm:

- `CatalogOutboxRelayRecord`, `CatalogOutboxRelayLaneStore`.
- `CatalogOutboxRelayCoordinator`, `CatalogOutboxRelayScheduler`.
- Progress coordinator/signal bridge; không tạo dependency cycle giữa relay và operation packages.

### P4 — Correctness và recovery gate — `PENDING`

Chạy trước benchmark:

1. Unit tests cho signal coalescing, bounded loop, operation-scoped claim và single-flight relay.
2. PostgreSQL IT cho exact cardinality, primary election, relationship no-op, version, retry/fence và unique outbox.
3. Ordering IT: input trước marker, marker trước input, duplicate signal và duplicate Kafka delivery.
4. **Happy-path proof:** tắt hoặc đặt scheduler delay rất lớn; 25K vẫn tới `CATALOG_COMMITTED` bằng direct signals.
5. **Recovery proof:** suppress một direct signal; scheduler fallback vẫn đưa operation tới terminal state.
6. Fail bất kỳ correctness/recovery gate nào thì sửa trong scope; không benchmark và không nới timeout.

### P5 — Benchmark 25K lần 1 — `PENDING`

- Chạy đúng `CatalogOperationEndToEndBenchmarkTest#measuresCombinedPipelineForTwentyFiveThousandInputRecords`.
- Ghi wall-clock cho ingest committed, shard ready, unit claimed/completed, snapshots created/published,
  operation committing, final watermark published và `CATALOG_COMMITTED`.
- Nếu `<= 3.000 ms`: `TARGET_MET`, chuyển P7.
- Nếu `3.001–4.000 ms`: `ACCEPTED_WITH_DEBT`, chuyển P7.
- Nếu `> 4.000 ms`: chỉ được mở P6 từ chính timeline của lượt này.

### P6 — Tối đa một residual fix — `PENDING_CONDITIONAL`

Chọn đúng một bottleneck lớn nhất, không quay lại danh sách giả thuyết chung:

- Coordination/wait còn lớn nhất: sửa đúng missing wake-up hoặc global scan/claim gây delay.
- Reconciliation SQL còn lớn nhất: chọn **một** trong comparison pre-aggregation hoặc relationship set diff;
  `tmp_catalog_changed_subject` và grouped snapshot aggregation đã tồn tại nên không implement lại.
- Relay/Kafka ACK còn lớn nhất: sửa lane wake/drain wave hoặc durable mark batch; không bỏ broker ACK.

Sau targeted correctness gate, chạy benchmark 25K lần 2 và cũng là lần cuối. Không thử candidate thứ hai.

### P7 — Close feature — `PENDING`

- `TARGET_MET`: <=3 giây, cập nhật evidence và đóng 25K optimization.
- `ACCEPTED_WITH_DEBT`: 3–4 giây, giữ stable implementation, ghi residual bottleneck vào TD-023 và chuyển BT-09E.
- `TARGET_NOT_MET`: >4 giây sau P6; dừng local tuning. Giữ direct path chỉ khi correctness/recovery pass và nhanh
  hơn baseline ít nhất 20%; nếu không thì revert candidate. Ghi evidence trung thực rồi chuyển BT-09E.
- Trong mọi trường hợp, 1M vẫn deferred tới workload/resource/SLO qualification riêng.

## Benchmark và scope budget

- Baseline đã có; tối đa **hai** combined benchmark 25K mới: sau P4 và sau P6 nếu cần.
- Không benchmark 250K/1M, không benchmark khi test đỏ, không đổi dataset để đạt target.
- Không tách canonical/snapshot thành hai transaction chỉ để giảm số đo; atomic canonical + outbox invariant được giữ.
- Không thêm shard/unit/worker cho 25K; mục tiêu là giảm hand-off, không che latency bằng concurrency.

## Definition of Done

- Có một code path business duy nhất được scheduler và direct signal cùng sử dụng.
- Happy path hoàn tất khi scheduler không tham gia; lost-signal recovery vẫn pass.
- Exact durable outputs và final broker ACK pass.
- Kết quả cuối được phân loại đúng `TARGET_MET`, `ACCEPTED_WITH_DEBT` hoặc `TARGET_NOT_MET`.
- Plan, benchmark evidence, `STATUS`, Catalog `CONTEXT` và `TD-023` được cập nhật; không còn plan cạnh tranh.
