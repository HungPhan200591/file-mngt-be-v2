# SC-01 — Question chain phỏng vấn

> Question bank sinh từ deep-dive và evidence hiện có của SC-01. Đây là học liệu, không phải source of truth của architecture hay feature status.

## Cách dùng

- Đi theo chuỗi `WHY → WHAT → HOW → FAILURE → TRADE-OFF → PROJECT → EVOLUTION`.
- Mỗi câu trả lời nhanh chỉ giữ keyword chính; khi luyện nói, mở rộng bằng evidence được link.
- Phạm vi hiện tại: overview SC-01, architecture touchpoints, cross-service deduplication, BT-01, BT-02 và BT-03. SSE, resume chính xác và các break task sau BT-03 chỉ là hướng tiến hóa, chưa phải capability hiện hành.

## Coverage matrix

| Chain | Foundation | Senior | Architect | Phạm vi hiện tại |
| --- | --- | --- | --- | --- |
| CH-01 Problem & workload | Có | Có | Có | SC-01 overview/roadmap |
| CH-02 Bounded scan pipeline | Có | Có | Có | Deep-dive + touchpoints |
| CH-03 Durable run & lease | Có | Có | Có | BT-01 + code scan run |
| CH-04 Chunk transaction & recovery | Có | Có | Có | BT-01/BT-02 + code |
| CH-05 Inventory seed & idempotency | Có | Có | Có | BT-02 + migration/integration test |
| CH-06 Service boundary & evolution | Có | Có | Có | Context/architecture + SC-01 |

## Rapid question chains

### CH-01 — Từ bài toán đến workload

1. **Hỏi:** Vì sao scan một triệu filesystem entry không nên là một vòng lặp đơn giản?
   **Đáp nhanh:** Vì thời gian chạy dài và dữ liệu lớn làm lộ memory growth, crash giữa chừng và retry duplicate. Mô hình phù hợp là **durable job với bounded resources**.
2. **Hỏi:** “Một triệu” đã chứng minh hệ thống đạt năng lực chưa?
   **Đáp nhanh:** Chưa. Đây là **lab scale**, còn phải có workload contract, baseline, SLO, retention và resource limit.
3. **Hỏi:** Bottleneck đầu tiên cần giả thuyết là gì?
   **Đáp nhanh:** Filesystem discovery, metadata I/O, parser, database write hoặc review query đều có thể là bottleneck. Không chọn cache/sharding/Kafka trước khi đo.
4. **Hỏi:** Tại sao phải chốt volume và traffic mix trước thiết kế?
   **Đáp nhanh:** Vì cùng “1M file” nhưng file size, directory depth, tỷ lệ thay đổi và số run đồng thời tạo áp lực hoàn toàn khác nhau.
5. **Hỏi:** Senior sẽ chứng minh giải pháp bằng gì?
   **Đáp nhanh:** Bằng benchmark và failure drill: throughput, heap, DB batch latency, thời gian recovery và số duplicate sau retry.

### CH-02 — Bounded discovery pipeline

1. **Hỏi:** Pipeline scan lớn gồm những bước nào?
   **Đáp nhanh:** Tạo job, claim lease, walk filesystem, đọc metadata, parse, commit chunk/checkpoint, review, bulk decision và outbox.
2. **Hỏi:** Vì sao `Files.walk()` phải lazy?
   **Đáp nhanh:** Để không materialize toàn bộ path vào heap; walker chỉ đưa từng entry vào buffer bị giới hạn.
3. **Hỏi:** Chunk và queue giải quyết hai vấn đề nào?
   **Đáp nhanh:** Chunk giới hạn kích thước transaction/persistence; queue bounded tách tốc độ filesystem khỏi DB và tạo backpressure khi DB chậm.
4. **Hỏi:** Nếu buffer đầy thì chuyện gì xảy ra?
   **Đáp nhanh:** Producer phải block hoặc flush theo policy, thay vì tiếp tục ăn memory. Backpressure là cơ chế bảo vệ correctness/resource, không phải lời hứa tăng tốc.
5. **Hỏi:** Tại sao không đưa Kafka vào giữa walker và `scan_db`?
   **Đáp nhanh:** Proposal/issue và checkpoint cần local atomicity trong Scan Service. Kafka chỉ bắt đầu sau approval/outbox, nơi cần boundary cross-service.
6. **Hỏi:** Chunk 500 là invariant hay cấu hình tối ưu đã được chứng minh?
   **Đáp nhanh:** Là cấu hình khởi đầu cho study; phải benchmark theo DB, row size, latency và heap trước khi gọi là tối ưu.

### CH-03 — Durable run, lease và ownership

1. **Hỏi:** Lease bảo vệ điều gì?
   **Đáp nhanh:** Lease xác định worker nào có quyền ghi tiếp run và chặn worker cũ ghi sau khi mất lease. Run cũ bị đóng `FAILED`, còn lần chạy mới tạo `scan_run` mới; không takeover cùng `runId`.
2. **Hỏi:** Vì sao cần `workerId` khi đã có `leaseUntil`?
   **Đáp nhanh:** `leaseUntil` cho biết thời hạn, còn `workerId` xác định owner của run để chặn worker cũ. Cả hai cùng với `status` tạo fencing đơn giản.
3. **Hỏi:** Hai request start cùng `rootKey` được chặn ở đâu?
   **Đáp nhanh:** Application check giúp trả lỗi sớm; partial unique index `ux_scan_run_running_root` ở PostgreSQL là lớp bảo vệ cuối.
4. **Hỏi:** Worker cũ commit sau khi lease mất thì sao?
   **Đáp nhanh:** `ScanChunkCommitter` kiểm tra `status`, `leaseUntil` và `workerId`, rồi ném `ScanLeaseExpiredException`; chunk/finalization không được ghi tiếp.
5. **Hỏi:** Checkpoint có phải snapshot chính xác của filesystem không?
   **Đáp nhanh:** Không. Nó chỉ là mốc dữ liệu đã commit; filesystem có thể đổi trong lúc scan, nên reconciliation/inventory matcher xử lý ở bước sau.
6. **Hỏi:** Vì sao dev baseline có thể rewalk sau crash?
   **Đáp nhanh:** Rewalk đơn giản hơn resume path tuyệt đối; unique key và transaction chunk ngăn dữ liệu business trùng. Resume chính xác là tối ưu tiến hóa sau khi đo bottleneck.

### CH-04 — Chunk transaction và failure recovery

1. **Hỏi:** Một chunk cần commit cùng nhau những gì?
   **Đáp nhanh:** Dữ liệu proposal/issue, inventory seed, counters/checkpoint và lease renewal cần cùng local transaction để checkpoint không vượt dữ liệu đã ghi.
2. **Hỏi:** Nếu inventory ghi thành công nhưng proposal ghi lỗi thì sao?
   **Đáp nhanh:** `REQUIRES_NEW` chunk transaction rollback cả inventory và proposal/issue; run không được advance checkpoint.
3. **Hỏi:** Vì sao không mở transaction cho toàn bộ một triệu file?
   **Đáp nhanh:** Transaction quá dài giữ lock, phình persistence context và rollback quá đắt. Chunk giới hạn blast radius và memory.
4. **Hỏi:** Retry cùng chunk có tạo duplicate inventory không?
   **Đáp nhanh:** Không, inventory dùng unique `(root_key, source_relative_path)` và `ON CONFLICT DO UPDATE`. Retry proposal/event cần unique key/idempotency riêng.
5. **Hỏi:** Nếu commit thành công nhưng caller timeout thì sao?
   **Đáp nhanh:** Retry có thể xảy ra; write path phải idempotent và trả trạng thái cuối từ database, không giả định mỗi request chỉ chạy một lần.

### CH-05 — Inventory seed, upsert và idempotency

1. **Hỏi:** Inventory key của SC-01 là gì?
   **Đáp nhanh:** `(rootKey, sourceRelativePath)` định danh file vật lý trong một configured root. `fileSize` và `fileModifiedAt` là fingerprint cho matcher tương lai.
2. **Hỏi:** BT-02 seed inventory cho file nào?
   **Đáp nhanh:** Full walk seed mọi regular file; parser chỉ phân tích file được profile hỗ trợ. Vì vậy inventory coverage không phụ thuộc extension parser.
3. **Hỏi:** Vì sao cần `ON CONFLICT DO UPDATE` dù đã chặn hai scan cùng root?
   **Đáp nhanh:** Vì scan lần hai là sequential nhưng vẫn gặp row cũ: phải cập nhật metadata, state và `last_seen_run_id`. Đây là idempotency qua nhiều run, không chỉ chống concurrency.
4. **Hỏi:** `saveAll()` có đủ cho inventory không?
   **Đáp nhanh:** `saveAll()` có thể giúp Hibernate batch insert/update khi đã biết entity state, nhưng không tự upsert theo composite business key. Native `ON CONFLICT` vừa atomic vừa batch được.
5. **Hỏi:** Batch có 499 insert và 1 update thì sao?
   **Đáp nhanh:** Cùng một prepared SQL batch; PostgreSQL quyết định từng row đi vào INSERT hay DO UPDATE dựa trên unique index. Không cần tách hai batch.
6. **Hỏi:** Redis có phải nơi check inventory không?
   **Đáp nhanh:** Không mặc định. PostgreSQL là source of truth; BT-03 nên batch lookup 500 key từ DB, chỉ thêm Redis sau khi benchmark chứng minh DB là bottleneck.
7. **Hỏi:** Test BT-02 cần chứng minh gì?
   **Đáp nhanh:** Hai scan giữ nguyên count, không duplicate key, cập nhật `lastSeenRunId`, lưu metadata và xử lý được boundary hai chunk.

### CH-06 — Boundary, ownership và evolution

1. **Hỏi:** Service nào sở hữu inventory?
   **Đáp nhanh:** `scan-service` sở hữu `scan_db` và quyết định cách seed/match inventory. Catalog không đọc trực tiếp bảng này.
2. **Hỏi:** Tại sao JDBC batch writer nằm ở `adapter.out`?
   **Đáp nhanh:** SQL/JDBC là persistence I/O; application chỉ điều phối use case/transaction và gọi operation semantic của adapter.
3. **Hỏi:** Domain có được phụ thuộc JPA/JDBC không?
   **Đáp nhanh:** Không. `ScanInventoryItem` chỉ là record metadata thuần; entity và batch writer nằm ngoài domain.
4. **Hỏi:** BT-03 sẽ thay đổi flow nào?
   **Đáp nhanh:** Vẫn full walk nhưng batch lookup fingerprint; path không đổi chỉ update last-seen, path mới/đổi mới đi qua parser. Không kéo Catalog batch API của BT-04/05 vào sớm.
5. **Hỏi:** Khi nào cần đổi contract xuyên service?
   **Đáp nhanh:** BT-02/BT-03 chỉ chạm Scan DB nên chưa cần REST/Kafka contract mới. BT-04/BT-05 mới cần contract owner, versioning và compatibility decision.
6. **Hỏi:** Trade-off của rewalk + dedupe so với resume chính xác?
   **Đáp nhanh:** Rewalk dễ hiểu và ít state nhưng đọc filesystem lặp lại; resume chính xác giảm I/O nhưng tăng độ phức tạp partition/checkpoint. Chọn sau khi có baseline.
7. **Hỏi:** Polling và SSE nên tiến hóa như thế nào?
   **Đáp nhanh:** Baseline hiện tại dùng polling qua `GET /api/v2/scans/{scanId}` vì đã có contract và tự khôi phục sau refresh. SSE là tối ưu tương lai, cần event schema, reconnect/heartbeat và không thuộc BT-03.

## Anchor interview questions

### A-01 — `FOUNDATION` · `COMMON_SCENARIO`
**Question:** Bạn thiết kế scan một triệu filesystem entry như thế nào?
**Interviewer evaluates:** Có bắt đầu từ bounded resource, durability và failure thay vì nhảy ngay vào Kafka/cache không.
**Trả lời 30 giây:** Tôi biến scan thành durable job: worker có lease, walker lazy, buffer/chunk bị giới hạn, commit dữ liệu và checkpoint cùng transaction. Sau đó mới thiết kế review, bulk decision và outbox; một triệu file là lab scale cần benchmark, chưa phải năng lực đã chứng minh.
**Answer spine:** workload contract → bounded discovery → lease/checkpoint → chunk atomicity → evidence.
**Project evidence:** [01-deep-dive.md](../01-deep-dive.md), [04-break-task.md](../04-break-task.md).
**Trade-offs:** Rewalk đơn giản hơn resume path chính xác; chunk nhỏ giảm blast radius nhưng tăng transaction count.
**Follow-up ladder:** Bottleneck nào? Nếu worker crash? Vì sao không Kafka giữa walker và DB?
**Red flags:** Nói “load toàn bộ vào memory”, “Kafka làm hệ thống chắc chắn hơn”, hoặc khẳng định 1M đã đạt mà không benchmark.

### A-02 — `SENIOR` · `PROJECT_APPLICATION`
**Question:** Làm sao bảo đảm chunk không advance checkpoint khi inventory/proposal ghi lỗi?
**Interviewer evaluates:** Hiểu transaction boundary và thứ tự commit.
**Trả lời 30 giây:** Đặt inventory, proposal, issue, counters/checkpoint và lease renewal trong `REQUIRES_NEW` của `ScanChunkCommitter`. Chỉ khi transaction commit thành công checkpoint mới advance; lỗi ở một side effect rollback cả chunk.
**Answer spine:** local transaction → all-or-nothing chunk → checkpoint meaning → retry.
**Project evidence:** `ScanChunkCommitter.commitChunk`, `ScanIntegrationTest`.
**Trade-offs:** Transaction chunk không bao toàn run; đổi lại rollback/recovery bounded.
**Follow-up ladder:** Caller timeout thì sao? Retry proposal có duplicate không? Lease cũ commit thì sao?
**Red flags:** Commit checkpoint trước data, transaction bao filesystem walk, hoặc gọi remote Catalog trong transaction.

### A-03 — `SENIOR` · `PROJECT_APPLICATION`
**Question:** Vì sao inventory dùng JDBC batch `ON CONFLICT` thay vì chỉ `JpaRepository.saveAll()`?
**Interviewer evaluates:** Phân biệt ORM batching với business-key upsert.
**Trả lời 30 giây:** `saveAll()` lặp `save()` và Hibernate có thể batch SQL khi entity state đã rõ, nhưng không tự tìm row theo `(rootKey, relativePath)`. JDBC batch với `ON CONFLICT` xử lý atomic upsert và cập nhật run/metadata trong một write path.
**Answer spine:** saveAll semantics → composite key → atomic conflict → batch.
**Project evidence:** `ScanFileInventoryBatchWriter`, migration V8, `application.yml` batch settings.
**Trade-offs:** Native SQL giảm abstraction JPA nhưng đặt persistence detail đúng adapter và phù hợp hot path.
**Follow-up ladder:** 499 insert + 1 update? Retry? Có cần lock không?
**Red flags:** Đồng nhất `saveAll()` với một SQL statement duy nhất, hoặc dùng Redis làm source of truth.

### A-04 — `ARCHITECT` · `ARCHITECTURE_EVOLUTION`
**Question:** BT-03 nên mở rộng BT-02 thế nào mà không kéo BT-04/05 vào sớm?
**Interviewer evaluates:** Biết giữ boundary và tiến hóa theo break task.
**Trả lời 30 giây:** Giữ Scan owner inventory, full walk và batch lookup fingerprint trong `scan_db`; path không đổi chỉ update `lastSeen`, path mới/đổi mới parse. Chỉ khi mở BT-04/05 mới thêm Catalog existence contract và cross-service call.
**Answer spine:** current owner → batch matcher → unchanged path → changed path → later boundary.
**Project evidence:** [04-break-task.md](../04-break-task.md), [03-cross-service-deduplication.md](../03-cross-service-deduplication.md).
**Trade-offs:** DB lookup mỗi batch đơn giản hơn Redis cache; cache chỉ là optimization sau benchmark.
**Follow-up ladder:** Mark MISSING ở đâu? Rename xử lý thế nào? Khi nào cần contract?
**Red flags:** Cho Catalog đọc `scan_db`, parse mọi file không đổi, hoặc triển khai BT-04 trong BT-03.

### A-05 — `ARCHITECT` · `COMMON_SCENARIO`
**Question:** Nếu hai request cùng start scan một root, bạn bảo vệ invariant ở đâu?
**Interviewer evaluates:** Biết phân biệt fast-path application check với database authority.
**Trả lời 30 giây:** Application kiểm tra run active để trả lỗi sớm, còn partial unique index trên `scan_run(root_key) WHERE status='RUNNING'` chặn race cuối cùng. Unique violation cần map thành `409 Conflict`; không dùng lock application tự chế thay cho invariant database.
**Answer spine:** race → fast path → unique index → error mapping → lease.
**Project evidence:** `V1__create_scan_preview.sql`, `ScanService`, `ScanExceptionHandler`.
**Trade-offs:** Database constraint đơn giản và chắc hơn advisory lock; request thua race có thể đã fetch snapshot trước khi INSERT fail.
**Follow-up ladder:** Lock row chưa tồn tại thế nào? Stale run và run mới khác nhau ra sao? Inventory key khác gì run key?
**Red flags:** Chỉ dựa vào `SELECT` rồi `INSERT`, hoặc cho rằng inventory upsert không cần vì scan run unique.

### A-06 — `ARCHITECT` · `ARCHITECTURE_EVOLUTION`
**Question:** Khi nào nên đổi polling trạng thái scan sang SSE?
**Interviewer evaluates:** Biết phân biệt baseline contract đang chạy với tối ưu realtime cần thêm boundary và failure semantics.
**Trả lời 30 giây:** Hiện tại dùng polling qua `GET /api/v2/scans/{scanId}` vì đơn giản, tự khôi phục sau refresh và đã có contract. SSE chỉ nên thêm khi polling trở thành bottleneck; khi đó cần event schema, reconnect/heartbeat, `Last-Event-ID` và chính sách connection riêng, không gộp vào BT-03.
**Answer spine:** current contract → polling baseline → measured bottleneck → SSE event contract → reconnect/failure.
**Project evidence:** [05-ui-ux-solution-behavior.md](../05-ui-ux-solution-behavior.md), `ScanController`.
**Trade-offs:** Polling tạo request định kỳ nhưng dễ vận hành; SSE giảm request thừa nhưng giữ connection dài và phải xử lý reconnect, duplicate event và backpressure.
**Follow-up ladder:** Event nào được publish? Client reconnect từ đâu? SSE có thay đổi scan transaction không?
**Red flags:** Xem SSE là cơ chế resume scan, hoặc thêm SSE trước khi có event/reconnect contract.

## Self-test

1. Vì sao “1M file” chưa phải SLO?
2. Bounded memory được bảo vệ ở những điểm nào?
3. Lease và checkpoint bảo vệ hai invariant khác nhau ra sao?
4. Chunk transaction rollback những dữ liệu nào?
5. Inventory key khác idempotency key của payment ở điểm nào?
6. Vì sao full scan vẫn cần seed cả file không được parser hỗ trợ?
7. `ON CONFLICT DO UPDATE` giải quyết sequential rescan thế nào?
8. Khi nào `saveAll()` đủ tốt cho persistence batch?
9. Nếu Redis mất trong BT-03, source of truth nào quyết định kết quả?
10. Vì sao BT-04/BT-05 mới là cross-service contract boundary?
11. Làm sao chứng minh batch 500 không làm phình heap?
12. Failure drill nào cần có trước khi tuyên bố SC-01 sẵn sàng?

## Evidence update log

| Ngày | Phạm vi | Evidence |
| --- | --- | --- |
| 2026-08-07 | BT-01/BT-02, inventory seed và chunk boundary | `ScanIntegrationTest`: 9 tests pass; fixture 504 file đi qua ít nhất 2 chunk. |
| 2026-08-07 | BT-03, matcher, MISSING, lease finalization | Scan module: 28 tests pass; rescan unchanged/modified/unsupported và missing đã có evidence. |
