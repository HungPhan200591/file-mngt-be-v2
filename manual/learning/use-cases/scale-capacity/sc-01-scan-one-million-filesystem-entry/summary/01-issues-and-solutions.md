# SC-01 — Summary: Issue, solution và câu chuyện phỏng vấn

Đây là study artifact, không phải source of truth triển khai. Trạng thái thật nằm ở các Feature Plan,
`apps/scan-service/CONTEXT.md` và [STATUS](../../../../../docs/STATUS.md).

## North star

> SC-01 biến scan một triệu filesystem entry thành durable pipeline có bounded memory, ownership
> được fencing, persistence theo set/chunk, quan sát được và approval phát event idempotent.

Keyword spine:

```text
Bounded → Lease → Chunk → Inventory → Set-based → COPY → Telemetry → Outbox
```

## Mental model 30 giây

```text
Filesystem
  ↓ lazy walk + bounded queue/backpressure
Discovery/staging
  ↓ materialize changed set một lần
Parallel analyze
  ↓ aggregate rồi single-thread commit
Chunk transaction
  ↓ data + checkpoint + lease fence cùng commit
Terminal run
  ↓ user decision
Decision + outbox cùng transaction → Kafka at-least-once → Catalog dedupe eventId
```

Một nguyên tắc xuyên suốt: **không tối ưu một phase bằng cách phá invariant của phase khác**.

## Các issue đã gặp và solution đã áp dụng

| Issue thực tế | Nguyên nhân gốc | Solution đã áp dụng | Trade-off / điều cần nói |
| --- | --- | --- | --- |
| Scan một vòng lặp giữ quá nhiều state | Toàn bộ path/result nằm trong heap và một transaction dài | Durable `scan_run`, lazy filesystem walk, queue bounded, chunk commit | Có nhiều checkpoint/transaction hơn; đổi lại memory và blast radius được giới hạn |
| Hai worker cùng ghi một root hoặc worker cũ ghi sau takeover | Không có durable ownership và compare-and-set | `workerId` + `leaseUntil` + status, unique partial index một root `RUNNING`, conditional fence trước/sau I/O | Lease bảo vệ quyền ghi, không tạo resume cursor hoàn hảo |
| Checkpoint vượt dữ liệu thật khi write lỗi | Checkpoint và business rows commit khác nhau | `REQUIRES_NEW`: proposal/issue, inventory, counters/checkpoint và lease update cùng transaction | Chunk rollback thì phải retry; transaction không được gom cả 1M file |
| Retry tạo proposal/inventory trùng | Không có business idempotency key | Unique `(scan_run_id, source_relative_path)` cho proposal; unique root/path cho inventory; upsert/constraint | Không cần exactly-once; retry vẫn có thể chạy lại nhưng không tạo business duplicate |
| Warm scan không đổi vẫn rewrite khoảng một triệu inventory row | `last_seen_run_id` buộc update mọi row chỉ để chứng minh file đã thấy | `UNLOGGED scan_inventory_stage` theo run, bulk `COPY`, set-based diff; chỉ update file mới/đổi/revived, anti-join mark `MISSING` | Filesystem vẫn phải walk toàn root; tối ưu database mutation, không biến thành watcher |
| 2.000 chunk/lookup cho một triệu file | Reconciliation xử lý theo chunk nhỏ và lookup lặp | Tăng batch nội bộ lên 10.000; discovery stream segment tối đa 500.000; changed set materialize một lần | Segment lớn không có nghĩa giữ 500.000 object trong heap; queue vẫn bounded |
| Có composite index nhưng diff vẫn gần O(N²) | Planner chỉ dùng `root_key`, path rơi xuống `Join Filter`; staging statistics stale | `ANALYZE` staging, keyset page bounded, correlated lookup theo đủ composite key | Phải đọc `EXPLAIN (ANALYZE, BUFFERS)`, không kết luận từ việc “đã có index” |
| JPA/JDBC batch proposal/issue không đạt mục tiêu | ORM mapping và batch round-trip nằm trên hot path | Parallel analyze bằng virtual threads; direct PostgreSQL `COPY` cho proposal/issue; inventory `INSERT ... SELECT` / `UPDATE ... FROM` | Domain parser vẫn ở Java; SQL chỉ nhận trách nhiệm persistence set-based |
| FK parent lookup làm COPY chậm | Mỗi proposal/issue insert phải kiểm tra FK tới `scan_run` | V13 bỏ hai FK proposal/issue → run; giữ `scan_run_id NOT NULL`, unique và FK decision/outbox → proposal | Chấp nhận mất FK parent hot path; hiện chưa có lifecycle xóa run, nếu thêm phải cleanup/audit orphan |
| UUID insert/index locality chưa phù hợp | UUIDv4 phân tán hơn trên write-heavy tables | PostgreSQL 18 + UUIDv7 policy/native default cho ID do service tạo | Rollout gắn với database reset/PG18; không tự chuyển UUID cũ từ application |
| Không biết chậm ở discovery, diff hay finalize | Log rời rạc, MDC có thể lặp key | FT-030 `ScanExecutionTimeline` theo `runId`: accepted, queue wait, discovery, diff, reconciliation, finalize và terminal aggregate | `runId` là durable job identity; `correlationId` chỉ là request context |
| Async logging có thể block hoặc drop log | Appender/queue policy không rõ | FT-029 đồng bộ config console/ECS JSON; Scan evidence giữ queue bounded và `neverBlock=false` | Đây là observability path, không được làm sai scan business flow |
| Buffered COPY thử nghiệm không cải thiện | Buffer 128 KiB không tạo chênh lệch đáng kể so với baseline | FT-031.2 revert helper/test buffer, giữ COPY đơn giản | Không giữ optimization chỉ vì nghe hợp lý; rollback là kết quả đúng khi evidence không ủng hộ |
| Cold root tốn update/anti-join dù chưa có inventory | Dùng warm algorithm cho root chưa từng có dữ liệu | FT-031.3 `SELECT EXISTS` sau lease validation; cold dùng set-based insert, warm giữ update + insert để bảo toàn semantics | Có hai execution mode; selector phải được test riêng |
| FE refresh/query khi scan còn `RUNNING` | Read query đụng các chunk chưa terminal | FT-028/027: không auto-refetch proposal/issue lúc RUNNING; SSE chỉ báo progress; terminal mới REST-verify/fetch | SSE best-effort, PostgreSQL/REST vẫn authoritative |
| Approval phát event trùng khi retry | Kafka publish và DB decision không nằm cùng transaction | Decision + transactional outbox cùng commit; publisher at-least-once; Catalog dedupe theo `eventId` | At-least-once không phải exactly-once; idempotency nằm ở consumer/business key |

## Sequence kể chuyện theo từng chặng

### Chặng 1 — Làm cho job sống được

Issue là scan dài, có thể crash và có hai worker tranh quyền. Solution là durable run, lease,
checkpoint, queue bounded và chunk transaction. Đây là nền safety/liveness trước khi tối ưu throughput.

### Chặng 2 — Không scan lại nghiệp vụ một cách mù quáng

Inventory lưu fingerprint `(root, relativePath, size, modifiedAt)`. Scan vẫn walk filesystem nhưng
chỉ parse path mới/đổi; staging giúp biết chính xác path đã thấy để mark `MISSING` cuối run.

### Chặng 3 — Giảm write amplification rồi mới giảm transaction amplification

Changed-only update giải quyết việc rewrite một triệu row. Sau đó set-based diff, batch 10.000 và
streaming segment giải quyết số round-trip/checkpoint. Hai bottleneck khác nhau, không dùng một tuning
để tuyên bố đã giải quyết cả hai.

### Chặng 4 — Tối ưu đúng boundary persistence

Parallel analyze phù hợp CPU-bound parsing; commit database vẫn tuần tự và lease-fenced. COPY/set-based
SQL phù hợp bulk persistence; business policy/evidence vẫn ở Java.

### Chặng 5 — Đo để biết solution có thật sự tốt hơn

FT-030 bổ sung phase timeline và commit evidence theo `runId`. FT-031 thử buffered COPY nhưng revert vì
không có lợi ích rõ; cold insert được giữ vì runtime evidence đạt mục tiêu dưới 30 giây cho fixture 1M.

## Default, project và điều chưa được chứng minh

| Loại claim | Nội dung |
| --- | --- |
| Nguyên tắc chung | Bounded queue tạo backpressure; chunk giới hạn rollback; lease cần fencing; outbox là at-least-once |
| Đã cấu hình/triển khai ở project | PostgreSQL 18, UUIDv7, parallelism mặc định 8, direct COPY, staging `UNLOGGED`, cold/warm inventory path |
| Evidence đã có | FT-030 runtime timeline; FT-031 cold run `1M`, `10/10 committed`, `durationMs=25763`, `inventoryWriteMs=3908` |
| Chưa được tuyên bố | Resume chính xác sau process restart/lease handoff; full failure-mode Testcontainers; chunk-size tối ưu toàn cục |
| Chưa làm | FT-033 read model; BT-04 Catalog batch existence API; targeted issue recheck TD-006 |

## Decision rules khi bị hỏi “tại sao không dùng X?”

1. Nếu bottleneck chưa đo, thì benchmark trước; không mặc định cache, sharding, Kafka hay tăng thread.
2. Nếu invariant cần local atomicity, thì giữ flow trong Scan DB; Kafka chỉ bắt đầu sau transactional outbox.
3. Nếu dữ liệu có thể tái tạo, thì staging có thể `UNLOGGED`; canonical inventory không được dựa vào scratch.
4. Nếu workload bulk, thì dùng set-based/COPY; không kéo hàng triệu entity qua JPA persistence context.
5. Nếu retry có thể xảy ra, thì thiết kế idempotency key và conditional fence; không giả định exactly-once.
6. Nếu optimization không có A/B evidence, thì revert; code ít hơn với correctness rõ thường là kết quả tốt hơn.

## Câu trả lời phỏng vấn 30 giây

SC-01 bắt đầu từ rủi ro của một scan dài: memory growth, transaction lớn, crash và worker tranh
quyền. Tôi tách thành durable run có lease/checkpoint, filesystem walk và queue bounded để tạo
backpressure, rồi commit theo chunk có atomicity và fencing. Khi benchmark cho thấy write amplification,
tôi thêm inventory staging và set-based diff, sau đó dùng parallel analyze nhưng giữ commit DB có kiểm
soát. Direct COPY và cold/warm SQL giảm persistence cost; FT-030 giúp đo phase thật. Approval dùng
transactional outbox và Catalog dedupe `eventId`, nên retry không tạo business duplicate. Những phần như
resume chính xác sau crash và FT-033 read projection vẫn là follow-up, không nói quá evidence.

## Answer spine 2 phút

1. **Problem:** một triệu entry là lab scale, không tự chứng minh capacity; cần workload/baseline/SLO.
2. **Safety:** durable `scan_run`, root ownership, lease `workerId`, conditional fence và unique constraint.
3. **Memory/liveness:** lazy walk, bounded queue, chunk transaction, checkpoint chỉ advance sau commit.
4. **Correctness:** inventory fingerprint và staging phân biệt unchanged/changed/revived/missing.
5. **Performance:** set-based reconciliation giảm write amplification; stream segment giảm round-trip;
   parallel analyze xử lý CPU; COPY xử lý bulk DB write.
6. **Evidence:** telemetry theo `runId`; đọc phase và commit outcome thay vì đoán từ tổng latency.
7. **Boundary:** Scan sở hữu `scan_db`; Catalog chỉ nhận event sau approval; outbox atomic, consumer dedupe.
8. **Trade-off:** bỏ FK parent để bảo vệ COPY nhưng giữ FK decision/outbox; revert buffered COPY vì không
   có lợi ích; giữ cold path vì có evidence.
9. **Honesty:** failure verification, exact resume, chunk-size tuning và FT-033 read model chưa được coi
   là DONE nếu chưa có evidence tương ứng.

## Traps cần tránh

- Gọi `1M` là SLO production.
- Nói bounded queue làm scan nhanh hơn; nó chủ yếu bảo vệ memory và tạo backpressure.
- Nói checkpoint là resume filesystem chính xác.
- Nói index tồn tại là query đã nhanh; phải xem `Index Cond` và execution plan.
- Nói parallel analyze đồng nghĩa parallel database commit.
- Nói `UNLOGGED` là nguồn sự thật.
- Nói outbox loại bỏ duplicate; nó chỉ bảo vệ handoff, consumer vẫn phải dedupe.
- Nói async projection sẽ không tranh tài nguyên với writer.

## Active recall

1. Vì sao một transaction cho cả 1M file nguy hiểm hơn nhiều chunk?
2. Lease khác lock ở điểm nào và vì sao cần `workerId`?
3. Vì sao staging giảm write amplification nhưng không giảm full filesystem walk?
4. Tại sao composite index vẫn có thể dẫn đến nested loop gần O(N²)?
5. Vì sao parallel analyze không được tự ý biến thành parallel DB commit?
6. Khi nào bỏ FK là trade-off chấp nhận được và invariant nào phải giữ lại?
7. Buffered COPY đã được thử và vì sao bị revert?
8. Cold path khác warm path thế nào?
9. Outbox bảo vệ khoảng thời gian failure nào? Catalog dedupe ở đâu?
10. Những phần nào của SC-01 hiện vẫn phải nói là deferred/VERIFY PENDING?

## Evidence map

- [FT-025 staging reconciliation](../../../../../docs/features/025-inventory-staging-reconciliation/03-plan.md)
- [FT-026 liveness guard](../../../../../docs/features/026-scan-run-liveness-guard/03-plan.md)
- [FT-027 SSE progress](../../../../../docs/features/027-scan-run-sse-progress/03-plan.md)
- [FT-028 parallel reconciliation](../../../../../docs/features/028-parallel-reconciliation-pipeline/03-plan.md)
- [FT-030 telemetry](../../../../../docs/features/030-scan-performance-telemetry/03-plan.md)
- [FT-031 persistence optimization](../../../../../docs/features/031-scan-reconciliation-persistence-optimization/03-plan.md)
- [Scan Service context](../../../../../apps/scan-service/CONTEXT.md)
- [Performance debug guide](../debug-performance.md)
- [Question chain](../question-bank/01-question-chain.md)

