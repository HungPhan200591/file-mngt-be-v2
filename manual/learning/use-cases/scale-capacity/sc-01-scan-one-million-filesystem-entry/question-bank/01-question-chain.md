# SC-01 — Question chain phỏng vấn

> Question bank sinh từ deep-dive và evidence hiện có của SC-01. Đây là học liệu, không phải source of truth của architecture hay feature status.

## Cách dùng

- Đi theo chuỗi `WHY → WHAT → HOW → FAILURE → TRADE-OFF → PROJECT → EVOLUTION`.
- Mỗi câu trả lời nhanh chỉ giữ keyword chính; khi luyện nói, mở rộng bằng evidence được link.
- Phạm vi hiện tại: overview SC-01, architecture touchpoints, cross-service deduplication, BT-01, BT-02, BT-03, FT-028 và FT-029–031. Chain bám theo API/UX bất đồng bộ, SSE/REST, evidence benchmark 1M, lease/checkpoint và trade-off persistence đã thử nghiệm; resume chính xác sau crash vẫn là follow-up.
- Update FT-025/BT-03F bổ sung staging reconciliation để sửa write amplification của BT-03; các câu CH-05 giữ nguyên bối cảnh lịch sử, CH-07 là behavior hiện hành sau migration V9.

## Coverage matrix

| Chain | Foundation | Senior | Architect | Phạm vi hiện tại |
| --- | --- | --- | --- | --- |
| CH-01 Problem & workload | Có | Có | Có | SC-01 overview/roadmap |
| CH-02 Bounded scan pipeline | Có | Có | Có | Deep-dive + touchpoints |
| CH-03 Durable run & lease | Có | Có | Có | BT-01 + code scan run |
| CH-04 Chunk transaction & recovery | Có | Có | Có | BT-01/BT-02 + code |
| CH-05 Inventory seed & idempotency | Có | Có | Có | BT-02 + migration/integration test |
| CH-06 Service boundary & evolution | Có | Có | Có | Context/architecture + SC-01 |
| CH-07 Staging reconciliation | Có | Có | Có | FT-025 + runtime evidence 1M |

| CH-08 FT-028 performance evolution | Có | Có | Có | JDBC/set-based benchmark, transaction 1M và batch 100k |
| CH-09 Evidence-driven API, UX & persistence tuning | Có | Có | Có | FT-029–031, runtime logs 1M, rollback 31.2 và cold path 31.3 |

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
8. **Hỏi:** Vì sao warm rescan có thể sinh proposal dù fixture không đổi?
   **Đáp nhanh:** Filesystem và PostgreSQL có thể khác precision timestamp. Ví dụ `...2029999Z` được DB làm tròn thành `...2030000Z`; nếu matcher floor riêng về millisecond thì hai phía thành `202` và `203`, gây false `NEW_OR_CHANGED`. Phải chuẩn hóa fingerprint theo cùng precision trước khi lưu và so sánh, không dùng tolerance tùy ý.

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
   **Đáp nhanh:** FE hiện nhận SSE progress khi run đang `RUNNING` và chỉ đọc REST authoritative ở terminal; SSE cần heartbeat/reconnect nhưng không thay thế lease hay transaction. Polling vẫn có thể dùng để khôi phục sau refresh.
8. **Hỏi:** FE phải làm gì nếu poll run trả `404` sau khi dữ liệu dev bị truncate?
   **Đáp nhanh:** Dừng polling ID cũ, vô hiệu hóa response stale, xóa `scanId` khỏi URL và reload recent runs; nếu history rỗng thì về empty state cho phép tạo scan mới. Không map `404` thành `FAILED`.

### CH-07 — Update FT-025: staging reconciliation

1. **Hỏi:** Vì sao BT-03 skip toàn bộ parser mà warm scan một triệu file vẫn mất khoảng 80 giây?
   **Đáp nhanh:** BT-03 vẫn full walk, lookup 2.000 chunk và upsert một triệu inventory row để đổi `last_seen_run_id`. Parser bằng 0 không đồng nghĩa filesystem I/O và database write bằng 0.
2. **Hỏi:** Staging trong FT-025 là gì?
   **Đáp nhanh:** Là bảng scratch `UNLOGGED` trong `scan_db`, chứa `(runId, rootKey, path, size, modifiedAt)` mà worker nhìn thấy trong run. Nó phục vụ reconciliation, không thay inventory durable.
3. **Hỏi:** Vì sao `last_seen_run_id` không còn ý nghĩa sau FT-025?
   **Đáp nhanh:** Tập staging theo `runId` đã trả lời chính xác file nào được thấy. Giữ `last_seen_run_id` chỉ lặp semantics và ép update/index churn trên mọi inventory row, nên V9 drop cột và index.
4. **Hỏi:** File không đổi được xử lý thế nào?
   **Đáp nhanh:** Vẫn COPY seen-item vào staging để bảo vệ MISSING, nhưng không parse và không update inventory. `updated_at` durable phải giữ nguyên.
5. **Hỏi:** File `MISSING` xuất hiện lại với fingerprint cũ thì sao?
   **Đáp nhanh:** Snapshot mang cả `state`; matcher coi `MISSING` tái xuất hiện là `NEW_OR_CHANGED` để changed-only upsert chuyển state về `PRESENT`.
6. **Hỏi:** Vì sao dùng `UNLOGGED` thay vì bảng thường?
   **Đáp nhanh:** Staging có thể tái tạo từ filesystem nên không cần trả WAL/replication cost. Sau crash PostgreSQL có thể truncate staging; run gián đoạn fail và run mới dựng lại, inventory canonical không mất.
7. **Hỏi:** Vì sao không dùng `TEMP TABLE`?
   **Đáp nhanh:** Temp table gắn với một database session, trong khi scan commit nhiều chunk/transaction qua connection pool. Shared `UNLOGGED` table keyed theo run tương thích lifecycle hiện tại hơn.
8. **Hỏi:** Transaction boundary mới bảo vệ gì?
   **Đáp nhanh:** Trong mỗi chunk, COPY staging, changed-only inventory upsert, proposal/issue và checkpoint cùng commit hoặc rollback. Finalization validate lease rồi mark MISSING, cleanup staging và complete run nguyên tử.
9. **Hỏi:** Staging có làm warm scan thành O(số file thay đổi) không?
   **Đáp nhanh:** Không. Nó giảm database mutation về O(file đổi) nhưng full filesystem discovery vẫn O(tổng file). Muốn gần tức thời cần journal/watcher với full-scan fallback.
10. **Hỏi:** Vì sao FT-025 ban đầu vẫn commit 2.000 chunk khi không có file đổi?
    **Đáp nhanh:** Seen path vẫn phải lookup, COPY vào staging, renew lease và advance checkpoint theo chunk 500. Changed-only inventory loại write amplification nhưng chưa loại transaction amplification.
11. **Hỏi:** FT-025.1 giảm transaction amplification thế nào?
    **Đáp nhanh:** Tăng reconciliation batch nội bộ lên 10.000 file, nên một triệu file còn tối đa 100 lookup/COPY/checkpoint transaction. Memory vẫn bounded; Catalog batch 500 là contract riêng và không bị thay đổi.
12. **Hỏi:** Vì sao FT-025.2 không đơn giản tăng lookup chunk lên 500.000?
    **Đáp nhanh:** Vì sẽ materialize hàng trăm nghìn object và câu `IN` vượt giới hạn parameter/thành query rất xấu. Discovery phải stream COPY, rồi PostgreSQL set-based diff sau khi walk xong.
13. **Hỏi:** Segment 500.000 có giữ 500.000 item trong heap không?
    **Đáp nhanh:** Không. `walkFileTree` producer dùng queue bounded 1.024 item; COPY consumer kéo và encode từng row, chỉ transaction row count đạt tối đa 500.000.
14. **Hỏi:** Worker mất lease giữa COPY segment thì sao?
    **Đáp nhanh:** Committer validate lease trước COPY và conditional-update checkpoint sau COPY. Nếu status/worker/lease không còn khớp, update bằng zero row, ném `ScanLeaseExpiredException` và rollback toàn segment.
15. **Hỏi:** Vì sao FT-025.2 có đủ composite index mà diff 27.122 file vẫn chạy hơn hai phút?
    **Đáp nhanh:** LEFT JOIN plan chỉ dùng `root_key`; path rơi xuống join filter, trong khi staging statistics stale báo 0 row. Nested loop thực tế gần O(stage × inventory-root), trái hẳn cost estimate.
16. **Hỏi:** FT-025.3 sửa query plan bằng cách nào?
    **Đáp nhanh:** ANALYZE staging, keyset page 25.000 row và correlated lookup để `(root_key, source_relative_path)` xuất hiện đầy đủ trong inventory `Index Cond`; page zero-change heartbeat lease. `business-chunk-size=100k` chỉ là upper bound.

### CH-08 — FT-028: performance evolution

1. **Hỏi:** JDBC batch benchmark 1M cho biết bottleneck ở đâu?
   **Đáp nhanh:** Đọc và classify gần như không đáng kể; ghi 1.000.000 inventory, 900.000 proposal và 100.000 issue mất khoảng 43–45 giây, nên bottleneck là **database write path**.
2. **Hỏi:** Set-based benchmark khác production scan ở điểm nào?
   **Đáp nhanh:** Benchmark set-based bỏ qua filesystem và analyzer, chỉ đo `INSERT ... SELECT`; 18–19 giây là **isolated lower bound**, không phải E2E SLO.
3. **Hỏi:** Vì sao proposal thường đắt hơn inventory?
   **Đáp nhanh:** Proposal có payload `evidence` lớn và phải duy trì UUID/index/unique/FK; inventory chủ yếu là metadata và natural-key lookup. Phải đo từng invariant, không suy đoán từ row count.
4. **Hỏi:** UUIDv7 có giải quyết bottleneck proposal không?
   **Đáp nhanh:** UUIDv7 có thể giảm random B-tree page split so với UUIDv4, nhưng benchmark chỉ cho thấy cải thiện một phần; payload và invariant vẫn là chi phí chính.
5. **Hỏi:** Vì sao gom 1M vào một transaction giảm từ 56,508s xuống 37,921s?
   **Đáp nhanh:** `scan_file_inventory` được set-based một lần thay vì 70 lần, giảm transaction/index amplification; E2E giảm khoảng 33%. Đây là experiment throughput, chưa phải transaction boundary cuối cùng.
6. **Hỏi:** Nhược điểm của transaction 1M là gì?
   **Đáp nhanh:** SSE proposal/issue chỉ nhảy một lần khi commit xong, và một lỗi rollback toàn bộ 1M. Blast radius và khả năng resume là trade-off lớn.
7. **Hỏi:** Page 25k thay đổi behavior và latency ra sao?
   **Đáp nhanh:** Với effective page 25k, 1M được xử lý khoảng 40 page/transaction thay vì 10 page 100k. Peak memory, rollback blast radius và thời gian giữ lease giảm; đổi lại số query/commit/checkpoint tăng khoảng 4 lần. Benchmark hiện tại không cho thấy latency cải thiện có ý nghĩa, nên đây là tuning bounded-memory/transaction.
8. **Hỏi:** Solution tiếp theo để vừa nhanh vừa có progress là gì?
   **Đáp nhanh:** COPY kết quả phân tích vào staging theo batch, phát SSE `staged/analyzed`, sau đó materialize business theo batch lớn và checkpoint. Resume chi tiết sau crash và retry staging là follow-up riêng.

### CH-09 — Tuning có evidence: API, UX và persistence

1. **Hỏi:** Vì sao tối ưu scan không bắt đầu bằng SQL mà bắt đầu từ API/UX bất đồng bộ?
   **Đáp nhanh:** `POST /api/v2/scans/previews` trả **202 Accepted** với `scanRunId` để UI không bị khóa bởi công việc dài. Durable run, progress và trạng thái terminal biến thời gian chờ thành trải nghiệm quan sát được.
2. **Hỏi:** SSE, REST và database chia trách nhiệm thế nào để vừa realtime vừa đúng?
   **Đáp nhanh:** **SSE** là kênh best-effort cho progress; REST là nguồn đọc authoritative sau refresh/reconnect; database mới quyết định lease, checkpoint và terminal state. Mất SSE không được làm scan dừng hoặc làm client tự kết luận sai.
3. **Hỏi:** Vì sao phải đo `httpAcceptedMs`, queue wait và các phase thay vì chỉ nhìn tổng thời gian scan?
   **Đáp nhanh:** Tổng thời gian không chỉ ra chậm ở nhận request, chờ worker, discovery hay persistence. **Terminal timeline** tách từng phase, còn telemetry chunk tách inventory/COPY/commit để giả thuyết có thể bị bác bỏ.
4. **Hỏi:** Log console và ECS JSON phục vụ hai đối tượng nào?
   **Đáp nhanh:** Console cần chứa **event ID + số liệu đọc ngay** để grep theo `runId`; ECS JSON giữ field có cấu trúc để aggregate/truy vết. Không được đưa cùng `runId` hoặc `correlationId` từ MDC và fluent key-value vì encoder JSON sẽ từ chối key trùng.
5. **Hỏi:** Vì sao bỏ đúng hai FK hot path proposal/issue → run nhưng giữ FK decision/outbox → proposal?
   **Đáp nhanh:** COPY proposal/issue theo chunk cần tránh parent lookup trên hot write path; **decision/outbox → proposal** vẫn là lifecycle invariant nên giữ `ON DELETE CASCADE`. Đổi FK không đồng nghĩa thêm delete run: hiện chưa có lifecycle production xóa run, tương lai phải dọn proposal/issue tường minh và audit orphan.
6. **Hỏi:** Cụ thể FK làm `INSERT`/`COPY` chậm hơn ở đâu?
   **Đáp nhanh:** Với mỗi child row, PostgreSQL phải kiểm tra parent tồn tại qua primary-key/unique index để bảo toàn referential integrity; một triệu proposal/issue nghĩa là một triệu lần kiểm tra, kèm CPU, cache/I/O và phối hợp lock cần thiết. `COPY` chỉ giảm protocol/parse ở phía client-server, **không bỏ qua constraint**, nên chi phí FK vẫn nằm trên hot write path.
7. **Hỏi:** FT-031.2 thử buffer COPY rồi rollback nói gì về cách tối ưu?
   **Đáp nhanh:** Ý tưởng hợp lý không đủ; benchmark cho thấy `proposalCopyMs` từ **7.578s** thành **7.623s**, không cải thiện rõ nên rollback. Tuning tốt là giữ invariant, đo cùng workload, rồi bỏ thay đổi không có evidence.
8. **Hỏi:** Vì sao cold inventory fast path giảm mạnh từ hơn 11 giây xuống khoảng 3.9 giây?
   **Đáp nhanh:** Root thật sự trống không có row để update hay probe anti-join; chỉ cần **INSERT … SELECT** từ diff stage. Cold run 1M đạt `durationMs=25.763s`, nhưng warm root vẫn phải dùng upsert để giữ changed/revived semantics.
9. **Hỏi:** Cold fast path có phải nâng cấp thuần túy cho mọi scan không?
   **Đáp nhanh:** Không; nó đúng khi classification **cold** đáng tin và một root không có writer cạnh tranh. Với manual SQL hoặc writer mới chen giữa classify và insert, unique constraint sẽ làm chunk fail/rollback thay vì tạo dữ liệu sai; vì vậy ownership và running-root invariant là một phần của tối ưu.
10. **Hỏi:** Vì sao chưa tự tăng business chunk lên 200k hay 500k trong FT-031.4?
    **Đáp nhanh:** Chunk lớn có thể giảm overhead nhưng tăng **peak memory**, rollback blast radius và thời gian giữ transaction/lease. Effective page hiện tại là 25k vì ưu tiên bounded-memory/transaction; benchmark chưa chứng minh đây là latency gain. Các mốc 100k/200k/250k/500k trong FT-031 là historical scale experiment, không phải current page size.

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
**Question:** Vì sao hot path FT-028 dùng direct `COPY` và set-based SQL thay vì chỉ `JpaRepository.saveAll()`?
**Interviewer evaluates:** Phân biệt ORM batching với business-key upsert.
**Trả lời 30 giây:** `saveAll()` vẫn tạo nhiều entity/statement và không mô tả tốt changed-set; `COPY` giảm protocol overhead còn `UPDATE ... FROM`/`INSERT ... SELECT` để PostgreSQL xử lý cả tập dữ liệu. Native SQL giảm abstraction JPA nhưng phù hợp hot path đã đo được bottleneck.
**Answer spine:** ORM overhead → COPY transport → set-based plan → transaction boundary.
**Project evidence:** `ScanFileInventorySetWriter`, `ScanProposalCopyWriter`, `ScanIssueCopyWriter`, `SetBasedReconciliationWriteBenchmark`.
**Trade-offs:** Page 25k giảm memory, rollback blast radius và lease hold time nhưng tăng số transaction/query/checkpoint; benchmark chưa chứng minh latency tốt hơn. `business-chunk-size=100k` vẫn chỉ là upper bound cấu hình.
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
**Trả lời 30 giây:** FE dùng SSE để hiển thị progress trong lúc run chạy và đọc REST authoritative ở terminal; polling chỉ là fallback/recovery sau refresh. SSE cần event schema, reconnect/heartbeat và backpressure riêng, không phải cơ chế resume scan.
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
| 2026-08-07 | FE resilience sau truncate và polling lifecycle | FE regression test: stale `scanId`, deep-link ngoài recent page và polling dừng ở terminal state. |
| 2026-08-07 | Runtime warm scan 1M phát hiện write amplification | Run `52e59625...`: 1.000.000 skipped, 0 proposal/issue, khoảng 80 giây nhưng 1.000.000 inventory row vẫn đổi `updated_at`; evidence mở FT-025. |
| 2026-08-07 | Microbenchmark one streaming COPY có staging index | `BenchmarkFullCopy`: walkFileTree + encode + IPC + COPY một triệu row vào TEMP table trong 2,890 giây; transaction rollback, chưa gồm inventory diff/finalization. |
| 2026-08-07 | FT-025 staging reconciliation | Code/migration/test source đã triển khai; verification và benchmark chưa chạy theo rule người dùng. |
| 2026-08-08 | FT-028 JDBC/set-based isolation | JDBC batch 1M khoảng 43–45s; set-based isolation khoảng 18–19s; proposal là writer đắt nhất trong benchmark. |
| 2026-08-08 | FT-028 E2E transaction experiment | Một transaction 1M đạt 37,921s nhưng SSE chỉ nhảy cuối và rollback toàn bộ khi lỗi; batch 100k đạt 43,069s với 10 mốc progress. |
| 2026-08-08 | FT-029–031 observability và tuning loop | Terminal/per-chunk telemetry phân tách inventory, proposal COPY, issue COPY và commit; COPY buffer 31.2 bị rollback vì không cải thiện rõ. |
| 2026-08-08 | FT-031.3 cold inventory fast path | Run `019fe018-7640-7ff9-b467-c855a050f963`: 1M file, 10/10 committed, 25.763s; `inventoryWriteMs=3.908s`, không WARN/ERROR. |
