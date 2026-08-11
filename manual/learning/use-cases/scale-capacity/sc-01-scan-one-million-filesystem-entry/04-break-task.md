# SC-01 — Break task triển khai

> Đây là bản đồ lát triển khai của SC-01, không phải ADLC Plan hay source of truth trạng thái dự án.
> Phần “hồi ký” phản ánh đúng Plan/code/evidence đã có; phần “tiếp theo” chỉ là dependency map để mở FT
> riêng, không cấp quyền code nhiều lát cùng lúc.

## Quy ước đọc

- Giữ Scan là owner `scan_db`; Catalog là owner `catalog_db`; không đọc chéo database.
- `DONE` chỉ dùng khi Plan owner đã ghi nhận hoàn tất. `IMPLEMENTED — VERIFY PENDING` nghĩa là code đã
  có nhưng còn thiếu test/runtime evidence được nêu trong Plan.
- Batch/chunk/queue size đã dùng trong một FT là cấu hình implementation có evidence cục bộ, không tự
  trở thành contract cho lát khác.
- Mỗi BT tương lai phải mở thành một FT ADLC riêng. BT chạm REST/Kafka/từ hai service phải chốt contract
  trước code.
- Hồi ký không viết lại lịch sử: BT-02/BT-03 vẫn mô tả baseline `lastSeenRunId`; FT-025 trở đi mới sở hữu
  staging/set-based pipeline hiện hành.

## Bản đồ hiện tại

| Lát | FT owner | Trạng thái theo Plan | Kết quả/gate chính |
| --- | --- | --- | --- |
| BT-01 — Durable run + lease | [FT-022](../../../../../docs/features/022-durable-scan-run-lease/03-plan.md) | `DONE` | Chunk commit `REQUIRES_NEW`, lease fence và checkpoint durable; đã có integration evidence. |
| BT-02 — Inventory seed | [FT-023](../../../../../docs/features/023-file-inventory-seed/03-plan.md) | `DONE` | Seed/upsert inventory theo `(rootKey, sourceRelativePath)`. |
| BT-03 — Inventory matcher | [FT-024](../../../../../docs/features/024-inventory-matcher/03-plan.md) | `DONE` | Skip parser cho fingerprint không đổi; exact `MISSING` theo baseline cũ. |
| BT-03F — Staging reconciliation | [FT-025](../../../../../docs/features/025-inventory-staging-reconciliation/03-plan.md) | `IMPLEMENTED — VERIFY PENDING` | Thay `lastSeenRunId` bằng `UNLOGGED` staging, changed-only inventory và set-based diff; còn thiếu verification owner ghi trong Plan. |
| BT-03H1 — Liveness/deadline | [FT-026](../../../../../docs/features/026-scan-run-liveness-guard/03-plan.md) | `IMPLEMENTED — VERIFY PENDING` | Deadline, statement timeout, stale-run expiry và terminal fence; còn thiếu test runtime/lease-loss. |
| BT-03H2 — Progress stream | [FT-027](../../../../../docs/features/027-scan-run-sse-progress/03-plan.md) | `DONE`, runtime verification còn chờ | SSE snapshot/progress/terminal, Gateway pass-through và FE fallback; không thay PostgreSQL source of truth. |
| BT-03H3 — Reconciliation throughput | [FT-028](../../../../../docs/features/028-parallel-reconciliation-pipeline/03-plan.md) | `DONE`, failure-mode verification deferred | Parallel analyze, direct `COPY`, set-based inventory, UUIDv7/PostgreSQL 18; đã có runtime evidence 1M. |
| BT-03H4 — Logging/telemetry | [FT-029](../../../../../docs/features/029-async-non-blocking-logging-foundation/03-plan.md), [FT-030](../../../../../docs/features/030-scan-performance-telemetry/03-plan.md) | FT-029 implemented/pending runtime; FT-030 `DONE — runtime verified` | Bỏ log hot-loop, async structured logging và timeline theo `runId` để đo phase/commit thật. |
| BT-03H5 — Persistence optimization | [FT-031](../../../../../docs/features/031-scan-reconciliation-persistence-optimization/03-plan.md) | `DONE` | Buffered COPY đã revert vì không có lợi; cold inventory fast path đạt 1M dưới 30 giây trên fixture đã ghi nhận. |
| BT-04 — Catalog existence provider | [FT-034](../../../../../docs/features/034-catalog-batch-existence-api/03-plan.md) | `IMPLEMENTED — VERIFY PENDING` | Catalog read-only API, set-based lookup và Flyway locator uniqueness đã có code; direct integration/migration evidence deferred. |
| BT-05 — Scan–Catalog filtering | [FT-035](../../../../../docs/features/035-scan-catalog-filtering/03-plan.md) | `IMPLEMENTED — VERIFY PENDING` | Scan gọi Catalog ngoài transaction, micro-batch ≤500, exact skip và evidence cho các classification còn lại; runtime verification deferred. |
| BT-06A — Review queue baseline | [FT-032](../../../../../docs/features/032-scan-review-queue/03-plan.md) | `DONE` code tối thiểu, verification/review còn chờ | Global queue/history hiện dùng offset và query anti-join lịch sử; chưa có index evidence. |
| BT-06B — Review read model | [FT-033](../../../../../docs/features/033-scan-review-read-model/03-plan.md) | `DRAFT — NOT READY` | Chưa chốt durable source/rebuild, handoff/fence, freshness, worker liveness và global cutover. |
| BT-06C — Targeted issue recheck | [TD-006](../../../../../docs/TECHNICAL_DEBT.md) | `WAITING` | Job recheck theo issue/list sau khi sửa file; không dùng full-root scan trá hình hoặc phá inventory/lease. |
| BT-07 — Durable bulk decision | Chưa mở FT | `WAITING` | Thay bulk một transaction/materialize-all bằng job persisted, chunk bounded và restart-safe. |
| BT-08A — Event contract/DLT alignment | [FT-036](../../../../../docs/features/036-event-contract-dlt-alignment/03-plan.md) | `IMPLEMENTED — VERIFY PENDING` | Contract v2, explicit consumer dispatch và DLT observer cho cả v1/v2; Kafka verification deferred. |
| BT-08B — Outbox backlog capacity | [FT-037](../../../../../docs/features/037-outbox-backlog-capacity/03-plan.md) | `IMPLEMENTED — VERIFY PENDING` | Bounded lease claim/`SKIP LOCKED`, conditional publish state và backlog metrics cho Scan/Catalog; runtime verification deferred. |

Dependency hiện hành:

```text
BT-01 → BT-02 → BT-03 → BT-03F/H1/H2/H3/H4/H5
                                      ├→ BT-04 → BT-05
                                      ├→ BT-06A → BT-06B ┬→ BT-06C
                                      │                  └→ BT-07 ───┐
                                      └→ BT-08A ─────────────────────┴→ BT-08B
```

BT-04 đã được triển khai độc lập với FT-033 vì chỉ cung cấp read-only Catalog provider. BT-05 là feature
riêng, đã tích hợp consumer Scan nhưng vẫn còn verification deferred. BT-07 phải theo sau quyết định read model/bulk candidate selection của BT-06B để không
đóng cứng thêm query anti-join hiện tại. BT-08A/BT-08B đã có code riêng; runtime evidence vẫn là gate trước
cutover hoặc tuning tiếp theo.

## Hồi ký BT-01 → BT-03H5

### BT-01 — Durable scan run + lease — FT-022

- Thêm `workerId`, `leaseUntil`, progress/checkpoint và chunk transaction `REQUIRES_NEW`.
- Worker mất lease không được commit/finalize; configured root unavailable trả lỗi trước khi tạo run.
- Evidence owner: 21/21 integration test đã xanh. Resume filesystem chính xác từ path N không được tuyên
  bố; rewalk/dedupe vẫn là giới hạn.

### BT-02 — File inventory seed — FT-023

- Full scan seed/upsert `scan_file_inventory` theo chunk, chưa đổi parser/proposal.
- Đây là baseline lịch sử có `lastSeenRunId`; schema/behavior hiện hành đã được FT-025 thay thế một phần.

### BT-03 — Inventory matcher — FT-024

- So fingerprint `fileSize + modifiedAt`; file không đổi bỏ parser/proposal, file mới/đổi mới analyze.
- Finalize lease-fenced mark inventory không được thấy thành `MISSING`.
- Test module Scan đã xanh tại thời điểm FT-024, nhưng warm scan 1M sau đó phát hiện write amplification.

### BT-03F — Staging reconciliation và các follow-up — FT-025

- `scan_inventory_stage` sở hữu “đã thấy trong run”; inventory durable chỉ ghi file mới/đổi/revive và
  finalization dùng anti-join để mark `MISSING`.
- Pipeline đã tiến hóa qua streaming `COPY`, materialized diff, statistics refresh, composite-key lookup
  và heartbeat cho page zero-change. Không còn mô hình “mỗi 500 path lookup rồi update `lastSeenRunId`”
  trên hot path hiện hành.
- Plan vẫn `IMPLEMENTED — VERIFY PENDING`: không đổi thành `DONE` chỉ dựa trên code hoặc benchmark của
  feature kế tiếp.

### BT-03H1 — Liveness/deadline — FT-026

- Bổ sung operation/statement timeout, one-shot deadline guard, conditional expiry và terminal-state
  protection cho run dài.
- Code đã có; timeout/lease-loss/runtime verification còn theo Plan FT-026.

### BT-03H2 — SSE progress — FT-027

- SSE là transient delivery; snapshot/durable checkpoint/terminal vẫn đọc từ PostgreSQL và REST là
  recovery/fallback.
- Progress tách discovery với reconciliation, coalescing/bounded connection và không fetch proposal khi
  run còn `RUNNING`.
- Runtime E2E Gateway/SSE/reconnect vẫn là verification deferred; không tuyên bố multi-instance fan-out.

### BT-03H3 — Parallel/hybrid persistence — FT-028

- Parallelism chỉ áp dụng cho analyze/parse; direct PostgreSQL `COPY`, set-based inventory và checkpoint
  vẫn commit có kiểm soát trong transaction lease-fenced.
- Runtime 1M đã có; crash/retry/lease-loss/Testcontainers đầy đủ vẫn deferred.
- Không dùng kết quả này để tăng thread/chunk tùy ý; FT-031 cho thấy mọi optimization phải có telemetry.

### BT-03H4 — Logging và performance telemetry — FT-029/FT-030

- FT-029 chuẩn hóa async structured logging và bỏ log spam hot-loop; feature cross-service này chỉ được
  hồi ký phần giúp SC-01 không bị logging che/méo bottleneck.
- FT-030 thêm execution/commit timeline và đã runtime verify theo `runId`; đây là evidence đầu vào cho
  FT-031, không phải một optimization tự thân.

### BT-03H5 — Evidence-driven persistence optimization — FT-031

- Đo từng phase/commit trước; thử buffered COPY nhưng revert vì không cải thiện rõ.
- Cold inventory fast path giữ transaction/fence và đạt run 1M dưới 30 giây trên fixture/evidence đã
  ghi trong Plan.
- Chunk-size sweep rộng và một số Testcontainers semantics vẫn deferred; không gọi cấu hình hiện tại là
  tối ưu toàn cục.

## Lộ trình hiện hành từ BT-04

### BT-04 — Catalog batch existence provider — FT-034 `IMPLEMENTED — VERIFY PENDING`

- Contract owner:
  [catalog-scan-existence-v1.yaml](../../../../../docs/contracts/openapi/catalog-scan-existence-v1.yaml),
  `POST /internal/v2/catalog/scan-existence`, request từ 1 đến 500 item.
- Catalog đọc snapshot `REPEATABLE_READ`, lookup set-based locator
  `storageKey + relativePath` rồi subject identity; trả `EXACT_ASSET_EXISTS`,
  `EXISTING_SUBJECT_NEW_ASSET`, `NEW_SUBJECT` hoặc `CONFLICT` theo `clientRef`.
- Endpoint read-only: không tạo subject/asset/outbox, không đọc `scan_db`, không route Gateway. Unique
  partial index locator non-null được thêm khi code; migration fail nếu có conflict, không tự cleanup.
- Code Catalog provider, Flyway unique locator và contract đã có. Direct Catalog integration test đủ
  decision table/batch/error, migration và query-count evidence vẫn deferred theo ưu tiên thông luồng.

### BT-05 — Scan–Catalog filtering — FT-035 `IMPLEMENTED — VERIFY PENDING`

Decision đã được ghi trong [FT-035 Design](../../../../../docs/features/035-scan-catalog-filtering/02-design.md):

1. Parse/analyze changed candidates từ materialized diff hiện hành; chia HTTP batch tối đa 500 độc lập
   với `scan.business-chunk-size`.
2. Gọi Catalog ngoài transaction persistence `REQUIRES_NEW`; không giữ DB transaction/lease lock trong
   lúc chờ network.
3. Validate response đủ đúng một result cho mỗi `clientRef`; missing/duplicate/unknown classification,
   `400`, `503` hoặc timeout phải fail closed. Không được mặc định thành `NEW_SUBJECT`.
4. `EXACT_ASSET_EXISTS` không tạo proposal; `EXISTING_SUBJECT_NEW_ASSET`, `NEW_SUBJECT` và `CONFLICT`
   tạo proposal/evidence phù hợp để reviewer thấy lý do.
5. Kết quả existence chỉ là advisory snapshot. Approval + outbox và Catalog consumer/constraint vẫn là
   write authority khi canonical data đổi sau lookup.
6. Chốt retry budget, deadline, partial-chunk failure và resume/rewalk semantics trước code; chỉ chọn
   HTTP concurrency sau benchmark.

Điểm dừng: E2E fixture có exact locator/new locator/conflict và Catalog unavailable; chứng minh exact bị
skip, các case còn lại vào review, không có partial durable chunk khi classification chưa hợp lệ.

### BT-06 — Review path ở quy mô lớn — thay cho “keyset đơn giản” cũ

#### BT-06A — Baseline FT-032 đã có

- API global review queue/history và bulk decision/reopen đã tồn tại, dùng `page/size` offset.
- Query hiện hành join inventory/run/decision và dùng anti-join proposal/issue lịch sử để chọn current
  item. FT-032 chưa thêm index vì chưa có `EXPLAIN (ANALYZE, BUFFERS)` evidence.
- Đây là code tối thiểu để dùng UI, không phải read path đã chứng minh ở 1M history.

#### BT-06B — FT-033 read model đang `NOT READY`

Trước khi code phải chốt đủ gate trong
[architecture review](../../../../../docs/features/033-scan-review-read-model/05-architecture-review.md):

- Durable delta hay async root rebuild; terminal handoff O(1) phải atomic với finalize.
- Root generation/fence và merge rule để projector cũ không ghi đè run/decision mới.
- Worker timeout, retry, stale reclaim, shutdown và resource budget riêng với scan hot path.
- Freshness/watermark semantics khi projection lag/fail/rebuild.
- Global queue rollout/cutover không trộn old/new source làm sai ordering/count/pagination.
- Bulk candidate selection cũng phải rời anti-join/materialize-all, không chỉ tối ưu GET.

#### Pagination sau cutover

Không còn mặc định index/cursor chỉ là `(scan_run_id, source_relative_path, id)`: review queue hiện xuyên
nhiều run/root và sort theo current-item semantics. Keyset/cursor chỉ được chốt sau khi FT-033 khóa read
model, global ordering, filter và freshness contract. Cho tới lúc đó offset là baseline có giới hạn, chưa
được tuyên bố scale-ready.

#### BT-06C — Targeted issue recheck — TD-006, chưa mở FT

Current behavior chỉ có full-root incremental scan. Khi người dùng sửa thủ công filename/nội dung của
một item trong issue worklist, chưa có cách recheck đúng item đó mà không walk lại toàn root.

FT trả [TD-006](../../../../../docs/TECHNICAL_DEBT.md) phải:

- Nhận `issueId` hoặc danh sách issue và tạo persisted async job có progress/terminal state; không xử lý
  một request lớn đồng bộ.
- Resolve lại path từ owner data/config, kiểm tra vẫn nằm trong configured root và đọc observation hiện
  tại; không nhận/lộ absolute path từ client.
- Ghi proposal/issue observation mới và cập nhật inventory an toàn, có idempotency, lease/fence và
  conditional rule khi file đổi tiếp trong lúc job chạy.
- Không sửa/xóa history issue cũ để giả vờ nó chưa xảy ra; read model/current worklist phải hội tụ theo
  semantics được FT-033 chốt.
- Không gọi full-root scan bên trong mỗi issue hoặc tái sử dụng scan lease theo cách làm hỏng run đang
  `RUNNING`.

BT-06C phụ thuộc ít nhất vào quyết định current-item/projection merge của BT-06B. Nếu mở trước FT-033,
Design phải chứng minh rõ write-model authority và handoff tương thích, không tự giả định issue đã có
resolved state.

### BT-07 — Durable bulk decision job — chưa mở FT

Baseline cần thay: `decideReviewQueue`/`reopenReviewQueue` hiện lấy toàn bộ candidate thành `List`, dựng
decision/outbox trong memory và ghi trong một transaction. Với queue lớn, đây là unbounded transaction,
rollback và heap risk.

FT tương lai phải:

- Tạo persisted job với filter/scope snapshot hoặc selection rule versioned; trả `202 + jobId` nếu REST
  contract đổi sang async.
- Claim candidate theo keyset/chunk bounded từ read model đã chốt ở BT-06B; write authority vẫn
  conditional trên decision/write model.
- Mỗi chunk ghi decision và approval outbox trong cùng transaction; `REJECT`/reopen không tạo event.
- Có lease/fence, progress, retry budget, stale reclaim, deadline, terminal `COMPLETED/FAILED` và
  idempotency cho request/chunk.
- Chứng minh crash/restart không double decision/event, concurrent user action không bị ghi đè và
  publisher chậm không kéo dài decision transaction.

Không gộp tuning publisher vào BT-07; backlog sinh ra là input đo cho BT-08.

### BT-08A — Đồng bộ event contract và DLT — FT-036 `IMPLEMENTED — VERIFY PENDING`

FT-036 bổ sung source of truth `media.file.discovered.v2.md`, Catalog dispatch theo `eventType` và DLT
observer theo dõi hai topic version. v1 không đổi payload; unknown version fail vào DLT thay vì bị đoán là v1.
Kafka retry/DLT/duplicate evidence còn deferred.

### BT-08B — Outbox backlog capacity — FT-037 `IMPLEMENTED — VERIFY PENDING`

FT-037 thay poll/saveAll bằng claim `SKIP LOCKED` + lease 30 giây cho Scan/Catalog outbox. Publisher commit
lease trước khi chờ Kafka và conditional update theo owner sau ack/lỗi; crash sau ack vẫn có thể duplicate,
Catalog tiếp tục dedupe `eventId`. Batch mặc định 20 được giữ bounded, có metrics pending/oldest age/success/failure.

Verification còn cần evidence queue age, publish rate, broker latency/failure và Catalog lag:

- Claim/lease/`SKIP LOCKED` hoặc single-owner policy khi có nhiều publisher instance.
- Bounded batch/concurrency giữ partition-key ordering và không làm connection/heap tăng vô hạn.
- Retry/backoff/poison handling, stale claim recovery, shutdown và terminal/operations visibility.
- Metric backlog age/count/attempt, alert/SLO và replay procedure; không dùng event/path làm metric label.
- Failure test ack-before-save, broker down/recovery, publisher crash, duplicate delivery và Catalog
  dedupe một business effect theo `eventId`.

Không chọn batch/concurrency mới chỉ vì bulk job tạo nhiều event; tuning phải dựa trên evidence.

## Verification/debt còn mở nhưng không tự biến thành BT mới

- FT-025: Testcontainers semantics và post-follow-up cold/warm benchmark theo Plan owner.
- FT-026: timeout, database stall, lease-loss và terminal race runtime verification.
- FT-027: E2E FE–Gateway–Scan reconnect/fallback/heartbeat; multi-instance fan-out chưa hỗ trợ.
- FT-028: COPY/set-based failure, retry, restart và lease-loss verification đầy đủ.
- FT-029: runtime verification cross-service còn chờ; FT-030 đã verify phần telemetry Scan.
- FT-031: chunk-size sweep và một số semantics Testcontainers deferred; cold 1M goal đã đạt.
- FT-032: integration/query evidence và architecture review; FT-033 là hướng read-path nền tảng nhưng
  chưa READY.

Các mục trên phải thực hiện theo Plan/feature hardening phù hợp khi được người dùng cho phép; không được
đánh dấu SC-01 hoàn tất chỉ vì cold fixture 1M đã đạt latency mục tiêu.

## Khi mở FT tiếp theo

1. Link đúng BT và dependency đã hoàn tất; chỉ lấy scope của một lát.
2. Đọc Plan owner gần nhất trước Brief/Design cũ; không giả định code còn giống baseline lịch sử.
3. Chạm REST/Kafka/database/từ hai service thì cập nhật contract/ADR khi cần trước code.
4. Ghi rõ evidence đã có, verification còn thiếu và điểm dừng; không gọi hypothesis là bottleneck đã
   chứng minh.

## Tham chiếu

- [Overview](./01-deep-dive.md)
- [Touchpoints](./02-architecture-touchpoints-and-flows.md)
- [Inventory và cross-service deduplication](./03-cross-service-deduplication.md)
- [Summary issue/solution/evidence](./summary/01-issues-and-solutions.md)
- [Trạng thái Backend V2](../../../../../docs/STATUS.md)
