# SC-01 — Khung mục tiêu hiệu năng & Chỉ số SLO (Chính thức)

Ngày chốt: 2026-08-22  
Owner: SC-01 Scale & Capacity
Phạm vi: SLO chính thức cho workload 1.000.000 files/records của Backend V2.
Cơ sở hardware và giới hạn vật lý: [Deep-dive Hardware & Physical Limits](../../../deep-dive/media-asset-management-architecture/02-performance-slo-and-hardware-benchmarks.md).

> Đây là target contract, không phải tuyên bố runtime đã đạt. P95 là mục tiêu chính; P99 là guardrail.
> Một lần chạy thành công không chứng minh được percentile SLO.

## 1. Workload và môi trường áp dụng

- `SLI-01`: cold scan 1M filesystem entries tới Scan proposal terminal.
- `SLI-02`: warm scan 1M entries, profile 0 changed files; changed-ratio phải ghi trong run manifest.
- `SLI-03`: approve 1M proposals đã tồn tại tới `QUERY_DB_READY`; thời gian bắt đầu là server-side
  operation accepted, kết thúc là Query watermark commit.
- `SLI-03C`: phase SLI riêng của Catalog, từ record discovery đầu tiên Catalog nhận tới khi broker ack toàn bộ
  final subject snapshots và `CATALOG_COMMITTED`. SLI này dùng input records làm mẫu số throughput.
- `SLI-04`: Query projection tới `SEARCH_READY`; đây là async lane và không chặn `QUERY_DB_READY`.
- Approve SLO không bao gồm filesystem scan trước đó, nhưng phải bao gồm Scan decision/outbox,
  relay, Kafka, Catalog coalesce/canonical write, Catalog outbox, Query projection và watermark.
- Run manifest bắt buộc ghi record count, subject cardinality/fan-out, payload profile, cache mode,
  observability profile và phase timestamps. Không cố định giả định `1M → ~150k subjects` cho mọi run.

Qualification environment phải được ghi rõ theo hardware deep-dive: CPU/RAM, NVMe/storage profile,
PostgreSQL/Kafka topology, partition/concurrency, connection pool và profile local/cloud. Đổi hardware,
storage class, topology hoặc observability profile thì phải re-qualify; không chuyển kết quả local thành
production capacity.

### Quy ước đo và điều kiện áp dụng

- SLO được tính trên các operation đã được server accept theo workload contract trong một measurement window
  rolling 7 ngày. Operation chưa có terminal state khi hết deadline được tính là vi phạm; không loại lỗi latency
  bằng cách chỉ lấy các run thành công.
- Mỗi cell qualification mặc định có **một operation 1M đang chạy tại một thời điểm**, không có operation
  approve 1M khác tranh chấp. Concurrent approve, traffic nền và queueing phải ghi trong manifest và được
  qualify thành cell riêng.
- Với tập đủ mẫu: ít nhất **30 run** để báo provisional P95; ít nhất **100 observation đủ điều kiện đo** để
  tuyên bố qualification P95/P99. Ít hơn ngưỡng này chỉ là benchmark evidence, không phải SLO pass.
- SLO latency là steady-state target khi dependency healthy, không phải hard deadline trong failover/outage.
  Failover vẫn phải giữ correctness, retry/replay và eventual completion; thời gian gián đoạn phải được
  báo riêng trong availability/error budget.

## 2. Bảng SLI/SLO chính thức

| Mã SLI | Nghiệp vụ | Target P95 | Guardrail P99 | Throughput tương đương | Trạng thái evidence |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`SLI-01`** | **Cold Scan 1M Files**: Walk → Staging → Parse → Proposal DB | **≤ 30s** | **≤ 45s** | **≥ 33.000 files/s** | FT-048 cold run: 25,371s / 39.415 files/s; các run gần đây khoảng 25–26s; chưa đủ qualification P95/P99 |
| **`SLI-02`** | **Warm Scan 1M Files**: periodic re-scan, 0 changed | **≤ 8s** | **≤ 12s** | **≥ 125.000 files/s** | Chưa đủ evidence lặp lại theo workload manifest |
| **`SLI-03C`** | **Catalog 1M Input Records**: first receive → final output broker ack | **≤ 33,334s** | **≤ 45s** | **≥ 30.000 input records/s; stretch 40.000** | FT-057 target; current V22 failed 25K và timeout 1M |
| **`SLI-03`** | **Approve 1M Records** tới `QUERY_DB_READY` | **≤ 60s** | **≤ 90s** | **≥ 16.700 records/s** | Target contract đã rebudget theo Catalog 30K; chưa có runtime evidence |
| **`SLI-04`** | **Search Ready 1M Records** qua Elasticsearch bulk async | **≤ 60s** | **≤ 90s** | **≥ 16.700 docs/s** | Async SLO riêng; chưa có runtime evidence |

## 3. Latency budget

Các số dưới đây là **budget allocation**, không phải phase measurement. Tổng mỗi critical path phải
khớp target; khi đo thực tế, phase nào vượt budget phải tạo hypothesis và benchmark riêng.

### 3.1. Cold Scan 1M — tổng P95 budget 30s

| Phase | Budget |
| --- | ---: |
| Filesystem discovery | 5,0s |
| PostgreSQL staging `COPY` | 3,0s |
| Set-based diff | 2,5s |
| Semantic parsing | 6,5s |
| Inventory + proposal/issue `COPY` | 11,5s |
| Checkpoint, lease fence, finalize | 1,5s |
| **Tổng** | **30,0s** |

### 3.2. Catalog 1M phase SLI (`SLI-03C`)

Boundary chuẩn:

- `catalogStartedAt`: thời điểm Catalog nhận record `media.file.discovered.v2` đầu tiên của operation;
- `catalogCompletedAt`: thời điểm broker ack cuối cùng giữa toàn bộ `media.subject.changed.v2` phải phát và
  watermark `CATALOG_COMMITTED`;
- `inputRecordsPerSecond = expectedDiscoveryRecordCount / (catalogCompletedAt - catalogStartedAt)`;
- output rate báo riêng bằng `changedSubjectCount / relayElapsed`, không dùng output cardinality nhỏ hơn để
  thay mẫu số input;
- gate tối thiểu `>= 30.000 input records/s` (`<= 33.334 ms` cho 1M), stretch `>= 40.000 input records/s`
  (`<= 25.000 ms`).

Workload qualification phải ghi rõ subject cardinality/fan-out. Profile chuẩn hiện tại là 1M input,
100K subjects, 10 assets/subject; output tối đa khoảng 100K final subject snapshots cộng watermark, không phải
1M output messages. Timer gồm ingest, durable stage, reduction, canonical/outbox và broker ack cuối.

Chi tiết architecture và implementation gate thuộc
[FT-057](../../../../../docs/features/057-catalog-bulk-reconciliation-data-plane/01-brief.md).

### 3.3. Approve 1M → `QUERY_DB_READY` — tổng P95 budget 60s

| Phase | Budget |
| --- | ---: |
| Scan decision + outbox commit | 5,0s |
| Scan outbox drain → Kafka | 4,0s |
| Catalog first receive → final output broker ack (`SLI-03C`) | 33,334s |
| Query bulk projection/upsert | 6,5s |
| Query watermark + cache generation switch | 0,5s |
| Queue/I/O/GC variance reserve | 10,666s |
| **Tổng** | **60,0s** |

`SEARCH_READY` không nằm trong budget `QUERY_DB_READY`; search có budget và backlog watermark riêng.

## 4. Correctness và pass/fail gate

Một operation chỉ được tính là thành công khi đồng thời:

- nằm trong percentile target của workload profile đã khai báo; percentile chỉ được kết luận ở cấp
  measurement window đủ mẫu, không kết luận từ một operation;
- có terminal state và watermark đúng operation/batch, kèm expected record/subject cardinality để reconciliation;
- không có unhandled DLT, duplicate canonical business effect hoặc mất record;
- decision + outbox atomic theo service owner; batch bounded, lease/fence và retry hợp lệ;
- resource metrics không cho thấy OOM, queue tăng vô hạn, pool exhaustion hoặc lease-loss.

Trạng thái hiện tại: `SLI-01` mới có baseline local một run; `SLI-02`, `SLI-03C`, `SLI-03` và `SLI-04` chưa
được qualification. Không dùng chữ `ĐẠT` cho tới khi có repeated runtime runs đủ để tính P95/P99.

Latency compliance là **ít nhất 95% operation ≤ target P95 và ít nhất 99% operation ≤ guardrail P99**;
data loss, sai watermark, canonical duplicate effect hoặc unhandled DLT là **0 tolerance** dù latency có đạt.

## 5. Qualification evidence bắt buộc

Workload ladder: `1K → 5K → 50K → 250K → 1M`, giữ cùng contract và ghi p50/p95/p99, records/s,
phase timing, Kafka lag, outbox pending/oldest age, SQL count, transaction duration, DB pool wait,
WAL/IOPS/lock, Redis latency, DLT, duplicate, retry và restart/reclaim.

SLO này phải được re-check khi thay đổi subject fan-out, payload profile, hardware/storage, Kafka
partition/concurrency, database index/schema hoặc observability configuration.
