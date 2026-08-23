# FT-063 — Event-driven happy path result

Ngày đo: 2026-08-23  
Kết luận: `TARGET_NOT_MET — CANDIDATE_ROLLED_BACK`

## Scope

- Workload: 25.000 discovery records → 2.500 subjects.
- Shape: một Kafka discovery partition, một ingest slice, một completion shard, một reconciliation unit.
- Happy-path proof: completion, finalizer và relay scheduler đều có initial delay 10 phút; direct signals phải tự
  đưa operation tới final broker acknowledgement.
- Không chạy 250K/1M.

## Candidate đã thử

1. Operation-scoped seal, claim, reconcile và begin-committing.
2. Coalesced progress signal sau ingest/watermark/shard-marker transaction return.
3. Durable relay ACK trả `operationId` để tiếp tục commit gate.
4. Single-flight direct relay wake; scheduler chỉ recovery.
5. V29 overload completion function giữ lock order operation → shard.

Targeted correctness/recovery gate trước benchmark đạt **30/30 PASS**, gồm PostgreSQL 18/Flyway V29,
operation-scoped paths, duplicate signal coalescing, relay single-flight và scheduler recovery khi mất signal.

## Hai lượt benchmark trong budget

### Lượt 1 — failure

- Operation dừng ở `RECONCILING` dù shard và unit đều `COMPLETED`.
- Pending outbox: `2.468`/`2.500`.
- Root cause: subject snapshots partition theo subject key trên 64 relay lanes; direct wake chỉ drain lane suy ra
  từ `operationId`, nên mới publish 32 events.

### Residual fix duy nhất

Direct relay query toàn bộ pending `relay_lane_id` của operation rồi drain tuần tự từng lane. Targeted gate sau
fix tiếp tục đạt **30/30 PASS**.

### Lượt 2 — final

| Metric | Kết quả |
| --- | ---: |
| Pipeline tới final broker ACK | 7.391 ms |
| Throughput | 3.382 records/s |
| Discovery seed | 1.917 ms |
| Completion marker seed | 85 ms |
| Watermark seed | 15 ms |
| Ingest | 1.030 ms |
| Finalizer acquire | 230 ms |
| Reconciliation unit | 2.372 ms |
| Complete operation | 1 ms |

Exact 25.000 input, 2.500 subjects và final broker ACK đều PASS. So với V28 `7.765 ms`, candidate giảm `374 ms`
hay khoảng `4,8%`; không đạt target 3 giây, ceiling 4 giây hoặc điều kiện giữ candidate tối thiểu `20%`.

## Quyết định

Rollback toàn bộ production source, V29 và test riêng của candidate; giữ V28 indexes và stable scheduler-based
runtime. Không chạy benchmark lần ba và không mở SQL candidate khác trong FT-063. Catalog capacity tiếp tục được
quản lý tại `TD-023` sau khi BT-09E/F hoàn tất và workload/resource/SLO được chốt.

## Command đã chạy

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\corretto-25.0.4'
$mavenArgs = @(
  '-Pbenchmark',
  '-pl', 'apps/catalog-service',
  '-am',
  '-Dtest=CatalogOperationEndToEndBenchmarkTest#measuresCombinedPipelineForTwentyFiveThousandInputRecords',
  '-Dsurefire.failIfNoSpecifiedTests=false',
  'test'
)
& .\mvnw.cmd @mavenArgs
```

## Verification sau rollback

- Chạy lại correctness gate trên source V28 sau `clean`: `26/26` test PASS, không có failure/error/skip.
- Flyway classpath sau clean chỉ còn migration đến `V28`; không còn artifact `V29` của candidate.
- Không chạy benchmark lần ba sau rollback, đúng giới hạn P7 đã chốt.
