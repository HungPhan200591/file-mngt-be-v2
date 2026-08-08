# FT-028 — Deep-dive hiệu năng reconciliation 1M file

Status: INVESTIGATED — chưa đạt SLO cold scan 1M file dưới 30 giây  
Owner: `scan-service`  
Ngày khảo sát: 2026-08-08  
Phạm vi: chẩn đoán và thiết kế hướng tối ưu; **không thay đổi code, schema,
runtime configuration hoặc contract trong tài liệu này**.

## 1. Mục tiêu và định nghĩa benchmark

Mục tiêu sản phẩm là **toàn bộ một scan 1.000.000 file hoàn tất dưới 30 giây**,
bao gồm:

```text
filesystem discovery → staging COPY → materialize diff → analyze
→ persist inventory/proposal/issue → checkpoint/finalize
```

Không được loại filesystem khỏi SLO. Cần công bố riêng ba loại benchmark để
tránh so sánh sai:

| Nhãn | Điều kiện | Ý nghĩa |
| --- | --- | --- |
| Cold filesystem | Cache OS chưa ấm, database đã sẵn sàng | Đo chi phí metadata thật của filesystem. |
| Warm filesystem, cold run | Cache OS ấm, root được scan lần đầu hoặc mọi file changed | Đo throughput pipeline cold reconciliation. |
| Warm reconciliation | Cache OS ấm, inventory không đổi, `changedFiles=0` | Đo chi phí scan định kỳ. |

Con số `17,832 giây` trong FT-025 là **17.832 giây**, là microbenchmark
filesystem-only trước đây; không phải 17.832 nghìn giây. Nó không thể thay thế
cho kết quả cold/warm của pipeline hiện tại nếu điều kiện cache không được ghi
nhận.

## 2. Evidence của run 1M mới nhất

Run: `35ff32a6-efa9-4643-a16b-f7d49c0f954f`  
Root key: `one-million-joke-video`  
Kết quả: 1.000.000 file changed, 890.000 proposal, 100.000 issue, terminal
`COMPLETED`.

| Pha | Thời gian | Evidence |
| --- | ---: | --- |
| Tổng scan | 84,651s | `ScanExecutor` start → complete |
| Discovery filesystem + staging COPY | 5,563s | start → discovery segment 2 |
| Materialize diff | 2,888s | log `durationMs=2886` |
| Reconciliation toàn pha | 73,962s | analyze đầu tiên → changed-chunk cuối |
| Analyze được log | 6,919s | tổng 70 log `ScanParallelAnalyzer.durationMs` |
| Cửa sổ post-analyze đến log commit | 64,508s | ghép 70 lần analyze-complete → commit log |
| Overhead reconciliation còn lại | 2,535s | page read, merge/list copy, progress, khoảng trống transaction |
| Finalize | 1,971s | changed-chunk cuối → finalize log |

Nguồn log: [`logs/scan-service.json.log`](../../../logs/scan-service.json.log).
Các mốc của run lần lượt ở dòng 2198 (start), 2210 (discovery), 2211
(materialize), 2422 (finalize) và 2423 (complete).

### Lưu ý về độ chính xác của phép đo commit

`ScanChunkCommitter` log commit **trước khi transaction thực sự return/commit**.
Do đó 64,508s là cửa sổ thực tế bao gồm merge, copy list, gọi writer và phần
SQL trước log; nó không phải timer DB thuần, đồng thời có thể còn thiếu phần
flush commit sau log. Tuy vậy đây là bằng chứng đủ mạnh rằng đường persist,
không phải analyze, chi phối latency.

## 3. Diễn biến các benchmark 1M trong log

| Run | Tổng | Discovery | Materialize |
| --- | ---: | ---: | ---: |
| `5fbf35b3...` | 123,470s | 4,520s | 2,666s |
| `3205bdcf...` | 103,794s | 4,055s | 2,315s |
| `64966e26...` | 89,555s | 6,344s | 3,382s |
| `7012f94d...` | 89,172s | 4,956s | 2,572s |
| `e48cac46...` | 90,150s | 5,659s | 2,981s |
| `35ff32a6...` | 84,651s | 5,563s | 2,888s |

V11 đã được áp dụng trước run mới nhất. Cải thiện từ khoảng 90s xuống 84,651s
chưa đủ ổn định để kết luận SLO đã được cải thiện đáng kể, nhất là khi các run
liền nhau có thể hưởng filesystem/database cache khác nhau.

## 4. Bottleneck đã xác nhận

### 4.1 Persistence của cold reconciliation là nút thắt quyết định

Cold run ghi khoảng 1,99 triệu row operation:

| Đích | Row |
| --- | ---: |
| `scan_file_inventory` | 1.000.000 |
| `scan_proposal` | 890.000 |
| `scan_issue` | 100.000 |

Sau run, dữ liệu trên PostgreSQL chiếm:

| Relation | Heap | Index | Tổng |
| --- | ---: | ---: | ---: |
| `scan_proposal` | 579 MB | 109 MB | 689 MB |
| `scan_file_inventory` | 157 MB | 154 MB | 311 MB |
| `scan_issue` | 14 MB | 13 MB | 28 MB |
| Tổng | 750 MB | 276 MB | 1.028 MB |

V11 đã loại bốn index trùng/thừa, nhưng write path vẫn phải duy trì sáu index
cần thiết. `scan_file_inventory` còn có PK UUID 38 MB và unique natural key
116 MB. `scan_proposal` buộc giữ UUID vì REST decision và event contract dùng
`proposalId`.

Hiện tại mỗi chunk gọi tuần tự:

```text
inventory JDBC batch upsert
→ proposal JDBC batch insert
→ issue JDBC batch insert
→ checkpoint
```

Xem [`ScanChunkCommitter.java`](../../../apps/scan-service/src/main/java/com/filemngt/v2/scan/application/scan/ScanChunkCommitter.java)
dòng 68–75.

`reWriteBatchedInserts=true` đã bật, nhưng direct PostgreSQL `COPY` vẫn có
overhead thấp hơn bulk `INSERT`/prepared batch theo tài liệu PostgreSQL 17.

### 4.2 Evidence proposal làm dữ liệu ghi phình lớn

`scan_proposal.evidence` trung bình **479 byte**; với 890.000 row, payload
text thô gần **426 MB**. Evidence đang lặp các field có thể dựng lại từ
`sourceRelativePath`, profile và các cột proposal: `fileName`, `fileStem`,
extension, parent path, path segments, identity source.

Evidence vẫn phải immutable để review/outbox dùng đúng ngữ cảnh lúc scan.
Tuy nhiên có thể lưu semantic tối thiểu/typed rồi reconstruct đúng REST
`evidence` tại read boundary, không đổi REST contract. Đây là cơ hội giảm WAL,
heap write và JSON serialization đáng kể.

Nguồn sinh evidence: [`ScanEvidenceCodec.java`](../../../apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/persistence/proposal/ScanEvidenceCodec.java)
dòng 63–73.

### 4.3 Analyze đã song song nhưng không phải bottleneck số một

Analyze 1M item mất 6,919s (trung bình 98,8ms/15.000 item). Parallelism=8 hoạt
động đúng, thể hiện bằng 8 partition trong log. Dù đưa analyze về 0s, tổng run
hiện tại vẫn xấp xỉ 77,7s; vì vậy chỉ tăng `reconciliationParallelism` không
thể đạt 30s.

Các allocation/hotspot cần xử lý sau persistence:

- Tạo `newVirtualThreadPerTaskExecutor()` cho từng chunk.
- Copy list khi partition, merge và tạo `ChunkBatch`.
- Tạo `Path` chỉ để check extension cho từng item.
- Tạo lại `HashSet` tag registry cho từng file.
- Regex, `LinkedHashMap` và Jackson serialization cho từng proposal.

Nguồn: [`ScanParallelAnalyzer.java`](../../../apps/scan-service/src/main/java/com/filemngt/v2/scan/application/scan/ScanParallelAnalyzer.java)
và [`SemanticParserSupport.java`](../../../apps/scan-service/src/main/java/com/filemngt/v2/scan/domain/semantic/SemanticParserSupport.java).

### 4.4 Filesystem vẫn thuộc SLO, nhưng evidence hiện tại chưa chứng minh cold latency

Discovery 5,563s của run mới nhất là tốt cho ngân sách tổng, nhưng run diễn ra
sau nhiều benchmark cùng fixture nên có khả năng cache OS đã ấm. Không được
coi đây là cold baseline thay cho 17,832s cũ.

`ScanFileInventoryCursor` tạo một `Signal`, một queue handoff và CSV `byte[]`
cho từng file. Đây là chi phí có thể giảm bằng handoff theo block và buffer
tái sử dụng; chỉ nên quyết định sau khi có số liệu producer wait/consumer wait.

Nguồn: [`ScanFileInventoryCursor.java`](../../../apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/filesystem/ScanFileInventoryCursor.java).

## 5. Lỗi cấu hình/tài liệu cần loại bỏ

Ba giá trị hiện mâu thuẫn:

| Nguồn | `businessChunkSize` |
| --- | ---: |
| `ScanProperties` Java default | 5.000 |
| `application.yml` default bind runtime | 15.000 |
| FT-028 Plan | 50.000 |

Runtime thực tế là 15.000 item/chunk. Với diff page 100.000, mỗi page bị chia
thành 7 chunk, tổng 70 transaction reconciliation cho 1M row.

Sửa chunk size đơn thuần chỉ giảm một phần nhỏ transaction overhead, không
giảm được khoảng 64s persist-side. Khi triển khai follow-up phải thống nhất
Java default, YAML default, Plan và benchmark matrix.

Nguồn: [`ScanProperties.java`](../../../apps/scan-service/src/main/java/com/filemngt/v2/scan/config/ScanProperties.java)
và [`application.yml`](../../../apps/scan-service/src/main/resources/application.yml).

## 6. Ngân sách để đạt SLO 30 giây

### Cold/warm-controlled target

```text
Discovery filesystem + staging COPY       ≤ 5,0s
Materialize diff                           ≤ 2,5s
Analyze + persistence (có overlap)        ≤ 19,0s
Finalize                                  ≤ 1,5s
Headroom                                  ≤ 2,0s
-----------------------------------------------
Tổng                                     ≤ 30,0s
```

Nếu giữ pipeline tuần tự, với discovery/materialize/finalize hiện tại,
persistence-side phải giảm từ khoảng 64,5s xuống khoảng 10s. Nếu có bounded
overlap giữa CPU analyze và DB writer, persistence-side có thể dùng gần 17s.
Do đó cần cải thiện writer khoảng 3,8–6,4 lần; chỉ tune parallelism/chunk size
không thể đạt mức này.

Nếu cold filesystem thật vẫn là 17,832s, ngân sách còn lại chỉ khoảng 12,2s
cho mọi pha sau discovery. Khi đó SLO cold dưới 30s cần đồng thời tối ưu mạnh
filesystem và persistence, hoặc thay đổi kiến trúc discovery incremental.

## 7. Hướng thiết kế follow-up

### P0 — Direct COPY proposal/issue và inventory/set-based persistence

1. Giữ Java parser/evaluator/evidence và parallel analyze. Nạp trực tiếp kết
   quả proposal/issue vào `scan_proposal` và `scan_issue` bằng PostgreSQL
   `COPY` theo bounded chunk.
2. Không thêm bảng parsed-result; không dùng SQL rule đơn giản để thay thế
   nghiệp vụ parser production.
3. Inventory dùng `INSERT ... SELECT` cho NEW và `UPDATE ... FROM` cho existing,
   tránh 1M JDBC upsert/conflict probe từ Java.
4. Giữ `REQUIRES_NEW`, lease fence, checkpoint và rollback bounded. Chỉ
   `scan_inventory_stage` và `scan_inventory_diff_stage` là scratch state;
   `scan_proposal`, `scan_issue` và `scan_file_inventory` là source of truth.
5. PostgreSQL 18 + UUIDv7 là prerequisite follow-up. Bất kỳ schema migration
   nào là V12 mới; V11 đã chạy, tuyệt đối không sửa lại. FK giữ nguyên phase đầu.

### P1 — Compact evidence và bounded pipeline overlap

1. Tách semantic immutable tối thiểu khỏi JSON evidence lặp field dẫn xuất.
2. Reconstruct full evidence map tại read boundary để giữ nguyên REST response.
3. Một DB writer tuần tự nhận bounded chunk đã analyze, trong khi CPU analyze
   chunk kế tiếp. Không để worker nền giữ connection trong analyze.
4. Reuse CPU executor và loại các list copy không cần thiết.

### P2 — Filesystem path và PostgreSQL benchmark profile

1. Đo cold/warm filesystem riêng; thêm timer walk, COPY encode, queue producer
   wait và consumer wait.
2. Dùng block handoff 2k–8k item và reusable buffer; chỉ thử parallel walk theo
   top-level directory trên NVMe sau benchmark. HDD có thể tệ hơn khi parallel.
3. Benchmark PostgreSQL hiện đang thấy `shared_buffers=128MB`, `max_wal_size=1GB`,
   `wal_compression=off`. Bulk load khoảng 1GB có nguy cơ kích hoạt checkpoint
   do `max_wal_size`; cần đo checkpoint/WAL delta trong cùng benchmark.
4. Với benchmark profile local chuyên dụng, xem xét tăng `max_wal_size` và
   memory hợp lý. Không tắt `fsync` hoặc `synchronous_commit` để đổi correctness
   lấy điểm benchmark.

PostgreSQL 17 khuyến nghị tăng `max_wal_size` khi bulk load để giảm tần suất
checkpoint; checkpoint có thể bị kích hoạt bởi `checkpoint_timeout` hoặc khi
WAL chạm `max_wal_size`.

## 8. Instrumentation bắt buộc trước benchmark kế tiếp

Không được chỉ dựa vào timestamp giữa các log. Cần thêm metric/timer có
`runId` trong log nhưng không biến `runId` thành Prometheus label:

| Timer/Counter | Mục đích |
| --- | --- |
| `discovery.walk`, `discovery.copyEncode`, `discovery.copyDb` | Tách filesystem khỏi COPY/encoding. |
| `diff.materialize` | Theo dõi planner/index regression. |
| `analyze.partition`, `analyze.merge` | Tách CPU parse khỏi merge allocation. |
| `persist.inventory`, `persist.proposal`, `persist.issue`, `persist.checkpoint` | Xác định chính xác writer chậm. |
| `transaction.afterCommit` | Đo đúng latency transaction, gồm commit flush. |
| rows/bytes từng writer | Tính throughput và phát hiện payload phình. |
| WAL/checkpoint delta trước/sau run | Phân biệt DB compute với checkpoint I/O. |

## 9. JDBC batch isolation benchmark

Benchmark `JdbcBatchReconciliationWriteBenchmark` seed 1.000.000 row vào
`scan_inventory_diff_stage` ngoài phần đo, sau đó chỉ keyset-read, classify
`Invalid*` và ghi 1.000.000 inventory, 900.000 proposal, 100.000 issue. Mỗi
batch có tối đa 50.000 row, mỗi batch ghi trong một transaction.

| Cấu hình | Total write | Chênh lệch |
| --- | ---: | ---: |
| JDBC batch mặc định | 44,557s | baseline |
| JDBC batch + `reWriteBatchedInserts=true` | 43,454s | -1,103s (-2,5%) |

Ở steady-state batch proposal-only, transaction mất khoảng 1,8–2,1s/batch;
inventory khoảng 0,7–0,8s và proposal khoảng 1,1–1,3s. Tổng hai writer gần
bằng thời gian transaction, nên commit không phải chi phí quyết định. Read
diff chỉ khoảng 32–41ms/batch, classify gần 0ms.

Kết quả này là lower bound lạc quan cho production vì proposal benchmark chỉ
ghi evidence `{}`, trong khi cold run thật có evidence trung bình 479 byte.
`reWriteBatchedInserts` vẫn phải giữ để tương thích production, nhưng không
phải đòn bẩy đủ lớn cho SLO. Direct `COPY`, inventory set-based và compact
evidence vẫn là P0/P1.

Lưu ý: pgJDBC chỉ gộp tối đa 32.768 row mỗi multi-value statement mặc định và
còn bị giới hạn bởi số bind parameter, do đó batch application 50.000 row có
thể thành nhiều statement vật lý.

## 10. Review conclusion

| Hạng mục | Kết quả |
| --- | --- |
| Data correctness/lease fence | PASS theo flow hiện tại; run 1M kết thúc `COMPLETED`. |
| FT-028 parallel analyze | PASS về chức năng, nhưng lợi ích bị Amdahl giới hạn. |
| JDBC batch + V11 index cleanup | PARTIAL; đã giảm overhead nhưng chưa chạm bottleneck write path đủ sâu. |
| Cold scan 1M < 30s | MISSING. |
| Cold/warm benchmark contract | MISSING. |
| Observability per-phase đủ để ra quyết định | PARTIAL. |

Verdict: **NOT READY cho SLO cold scan 1M file dưới 30 giây**. Bước triển khai
đúng thứ tự là P0, sau đó benchmark có instrumentation; P1/P2 chỉ được chốt
theo bottleneck còn lại đo được.

## 11. Quyết định triển khai sau benchmark 2026-08-08

Benchmark isolation đã ghi tại
[`benchmark/BENCHMARK_RESULTS.md`](../../../apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/BENCHMARK_RESULTS.md).
Set-based đơn giản đạt khoảng 18,7–19,3s persistence cho gần 2M row; đây là
evidence cho hướng tối ưu DB, không phải SLO production đã đạt.

Phạm vi follow-up được chốt:

- Giữ parallel analyze hiện tại; thay lớp persistence phía sau analyzer bằng
  direct `COPY scan_proposal`/`scan_issue` + set-based inventory write.
- Nâng PostgreSQL lên 18 trong study environment reset từ đầu và chuẩn hóa
  UUIDv7 cho ID mới.
- Giữ FK trong phase đầu; chỉ review bỏ FK sau correctness/retry/cleanup và
  benchmark production-like.
- Khi `RUNNING`, FE chỉ nhận SSE progress, không pull proposal/issue; terminal
  mới fetch authoritative data qua REST.

Kết quả SLO sau triển khai sẽ ghi bổ sung tại benchmark folder; hiện chưa tuyên
bố đạt mục tiêu tổng scan dưới 30s.
