# Backend V2 — Review chất lượng, kiến trúc và Production Readiness

> Ngày review: **2026-08-12**  
> Commit khảo sát: **`45adade8d67c`**  
> Phạm vi: toàn bộ production source/config của `gateway-service`,
> `catalog-service`, `scan-service`, `query-service`, `media-worker`,
> `platform/*`, migration và contract trực tiếp liên quan tới SC-01.  
> Workload tham chiếu: [SC-01 — Scan một triệu filesystem entry](../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/README.md)
> và [ghi chú performance/cloud scaling](../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/06-performance-and-cloud-scaling.md).

## Kết luận điều hành

**Verdict: `NOT READY` cho production/cutover.**

Nền tảng đã có nhiều quyết định đúng: service sở hữu database riêng,
transactional outbox, consumer idempotent/versioned, scan discovery bounded,
PostgreSQL `COPY`, set-based reconciliation, checkpoint có lease fence,
fallback PostgreSQL khi Redis/Elasticsearch lỗi và metric backlog cơ bản.

Tuy nhiên còn các blocker ảnh hưởng trực tiếp tới confidentiality, correctness
hoặc liveness của flow dài hạn:

- Chưa có trust boundary production: API không có authn/authz; Nginx expose
  toàn bộ drive read-only; route operation/admin đang mâu thuẫn với contract.
- Startup của mỗi replica đánh dấu **toàn bộ** `RUNNING scan_run` là `FAILED`,
  có thể phá run đang được replica khác xử lý trong rolling deployment.
- Filesystem walker blocking không có deadline/cancellation có thể kiểm soát;
  lease hết hạn không đồng nghĩa thread đã dừng.
- Issue recheck và bulk decision có lease claim nhưng các state transition sau
  khi xử lý không conditional theo `leaseOwner`; worker stale có thể ghi đè
  kết quả sau khi job đã được reclaim.
- Outbox Scan claim tối đa 500 nhưng publish tuần tự bằng `join()` và không có
  timeout; ngân sách 30 giây không được bảo vệ khi backlog lớn.
- Query projection đã có retry/DLT publisher, nhưng chưa có DLT
  observer/operator record, replay runbook và runtime verification; poison event
  recovery chưa đủ chứng minh.

Các mục còn lại chủ yếu là evidence gap, chi phí/retention và maintainability.
Chi tiết được phân loại thành backlog `TD-009` … `TD-022` trong
[`docs/TECHNICAL_DEBT.md`](../TECHNICAL_DEBT.md).

Các debt `TD-004` … `TD-008` đã tồn tại trước review này và được giữ nguyên
trong snapshot; chúng không nằm trong scope re-audit sâu của session (không có
claim đã trả nợ). Khi xử lý tiếp, owner dùng điều kiện trả nợ trong
[`docs/TECHNICAL_DEBT.md`](../TECHNICAL_DEBT.md) và cập nhật evidence tại Feature
Plan/commit tương ứng.

## Cách review và giới hạn bằng chứng

Review áp dụng `$architecture-quality-review`, `$production-readiness-review`,
`$load-v2-context`, `$maintain-v2-project-context` và router
`$study-use-case-scenario` cho workload SC-01. Đã đọc architecture summary,
coding rules, context của các service, ADR/contract trực tiếp và code/config
liên quan.

Không chạy Maven, test, migration, Testcontainers, Docker Compose hay service
runtime vì rule dự án chỉ cho phép khi người dùng yêu cầu rõ. Do đó:

- `PASS` nghĩa là có invariant/code evidence tĩnh đủ rõ.
- `PARTIAL` nghĩa là code có cơ chế nhưng chưa có race/failure/load evidence.
- `MISSING` nghĩa là chưa thấy cơ chế hoặc tài liệu/runbook cần cho production;
  không suy luận rằng framework tự cung cấp hành vi an toàn.

Snapshot khảo sát: **276 production Java file**, **24 test Java file**, không file
production nào vượt hard limit 500 dòng; có 7 file vượt soft limit 250 dòng và
8 package vượt ngưỡng 8 production type theo coding rules.

## Workload contract SC-01 được dùng trong review

| Trục | Evidence hiện có | Nhận định |
| --- | --- | --- |
| Volume | 1M filesystem entry; benchmark discovery/COPY cục bộ | Là lab scale, chưa là production capacity |
| Memory/batch | queue 1.024; discovery segment 500.000; diff page 100.000; business batch cấu hình được | Có bounded path, nhưng cần chứng minh dưới nhiều storage/DB profile |
| Hot path | filesystem → staging → diff/analyze → `scan_db`; Kafka chỉ sau approval | Kiến trúc tách đúng bottleneck scan và approve |
| Persistence | `COPY`, `INSERT ... SELECT`, checkpoint từng chunk | Có evidence tối ưu từ FT-031; chưa verify migration/query plan trong phiên này |
| Async state | `RUNNING`/lease/checkpoint, bulk/recheck/projection jobs | Terminal/reclaim/fencing chưa đồng đều |
| SLO | Chưa có SLO/queue-age/error budget end-to-end | Production gate còn thiếu |
| Growth/cost | Local Kafka một broker, replication factor 1; chưa có quota/retention budget | Chỉ phù hợp local, không suy ra cloud readiness |

Benchmark trong SC-01 ghi discovery/COPY khoảng 346k file/s ở máy local và một
benchmark FT-031 dưới 30 giây trong điều kiện riêng. Các số này **không** đo
toàn bộ reconcile, approve → outbox → Kafka → Catalog và không được dùng làm SLO.

## Những điểm đang làm đúng

1. **Ownership/boundary:** `scan_db`, `catalog_db`, `query_db` tách theo service;
   Catalog là canonical, Query là projection, Redis/Elasticsearch không là
   source of truth.
2. **Scan boundedness:** `ScanFileInventoryCursor` dùng queue bounded;
   reconciliation dùng `COPY`/set-based SQL và không giữ toàn bộ 1M item trong
   một collection.
3. **Safety của checkpoint:** `ScanRunProgressWriter` conditional theo
   `runId + status=RUNNING + workerId + lease_until`; stale scan worker khó
   commit checkpoint/finalize sau khi lease mất.
4. **Consistency:** approval ghi decision và outbox trong local transaction;
   Catalog/Query có dedupe/version guard; delivery at-least-once được ghi trong
   event contract.
5. **Degraded read:** Query có PostgreSQL fallback khi Elasticsearch lỗi và
   Redis cache không phải dependency bắt buộc của canonical read.
6. **Observability nền:** durable `runId`, correlation/trace propagation,
   backlog/oldest-age metric và ECS log đã có; metric label tránh path/identity.

Các điểm trên là nền tảng tốt nhưng không thay thế failure/load/deployment
evidence cần để đổi verdict.

## Findings ưu tiên

Severity theo skill: `CRITICAL` chặn confidentiality/boundary nghiêm trọng;
`HIGH` có thể làm flow chính sai, không terminal hoặc không recover; `MEDIUM`
là performance/maintainability/evidence gap đáng kể; `LOW` là debt nhỏ.

| ID | Mức độ | Pillar/control | Location/evidence | Tác động | Hướng sửa nhỏ nhất |
| --- | --- | --- | --- | --- | --- |
| `PR-SEC-01` / `TD-009` | **CRITICAL** | Security, boundary | `infra/nginx/nginx.conf:18-48`, `infra/compose/compose.yaml:9-13`; tất cả controller không có security chain; `MasterDataController` dùng `@CrossOrigin(origins="*")` | Người gọi có thể đọc file bất kỳ trong D/E/G đã mount và gọi mutation/admin API; confidentiality/integrity không được bảo vệ | Tách profile local khỏi production; chỉ mount/alias approved media roots; đặt authn/authz ở Gateway/Nginx, network policy và secret/TLS; bỏ wildcard CORS |
| `PR-CON-01` / `TD-010` | **HIGH** | Reliability, deployment | `ScanService.cleanupOrphanRunningScans()` (`ScanService.java:169-181`) | Mỗi replica startup fail mọi run `RUNNING`, kể cả run còn lease hợp lệ ở replica khác; rolling restart làm mất scan | Recovery chỉ xử lý run có lease quá hạn/owner không còn heartbeat; dùng conditional update/fencing; không blanket cleanup tại `ApplicationReadyEvent` |
| `PR-LIV-01` / `TD-011` | **HIGH** | Reliability, liveness | `ScanFileInventoryCursor.walk()` (`ScanFileInventoryCursor.java:51-58`) gọi `Files.walkFileTree`; `close()` chỉ `interrupt()` producer (`:73-78`) | Filesystem/network mount treo có thể giữ virtual thread vô hạn; deadline DB đánh dấu FAILED nhưng không thu hồi I/O/thread, gây resource leak và scan chồng | Dùng access primitive có deadline/cancellation thực sự; kiểm tra progress watchdog; tách worker process nếu cần hard kill; giới hạn số walker/root và đo no-progress |
| `PR-LIV-02` / `TD-012` | **HIGH** | Reliability, fencing, correctness | Recheck `IssueRecheckClaimService`/`IssueRecheckPersistence`; bulk `BulkDecisionClaimService`/`BulkDecisionJobWorker` | Claim có lease nhưng `complete/fail/progress` tải entity theo id và save không kiểm tra `leaseOwner`/lease; worker stale sau reclaim có thể hoàn tất/FAILED job của worker mới | Mọi transition dùng `UPDATE ... WHERE status=RUNNING AND lease_owner=? AND lease_until>now()`; giữ attempt/owner token; idempotent enqueue theo issue/request key; test crash/reclaim/concurrent completion |
| `PR-PERF-01` / `TD-013` | **HIGH** | Performance, reliability, cost | `ScanOutboxPublisher.java:67-97`; `KafkaOutboxMessagePublisher.publish()` (`:20-24`); `ScanOutboxClaimService` lease 30s; config batch có thể 500 | Publish tuần tự, chờ `join()` không deadline; batch có thể hết lease trước khi xong, tạo duplicate/retry storm và backlog không kiểm soát | Giới hạn in-flight bounded, timeout/deadline per send, batch/async ack; đặt `claim limit × worst-case send time < lease`; metric publish latency/lease-loss và backpressure |
| `PR-CON-02` / `TD-014` | **HIGH** | Contract, correctness, reliability | Gateway config route `/api/v2/catalog/operations/**`; `docs/contracts/http/gateway-routing-v1.md` vừa route vừa nói operations không public; `CatalogQueryProjectionReplayService.java:20-30` | Admin replay có thể bị expose nhầm qua Gateway; với subject đã có outbox `(subject_id, subject_version)`, replay có thể đụng unique constraint (`V3`) và rollback transaction lớn | Chốt contract/owner (direct admin port hoặc auth-protected route); replay durable/idempotent bằng existing event key hoặc Query rebuild job; bounded page/checkpoint, không giữ một transaction toàn dataset |
| `PR-REL-01` / `TD-015` | **MEDIUM** | Reliability, messaging, operations | `QueryKafkaErrorHandlingConfig.java` đã có `DefaultErrorHandler` + DLT publisher; `MediaSubjectChangedConsumer.java:22-28` chỉ nhận event, chưa thấy DLT observer/operator record/replay runbook | Poison event có terminal DLT path nhưng không có bằng chứng operator nhìn thấy, replay an toàn hoặc runtime retry/DLT behavior; lỗi có thể nằm im trong DLT | Thêm DLT observer/metric/operator record, replay procedure với idempotency/version guard và test malformed, duplicate, out-of-order, restart |
| `PR-PERF-02` / `TD-016` | **MEDIUM** | Performance, cost, maintainability | `CatalogFileDiscoveryService.handleV2()` kiểm tra/tạo Actress từng tên; `SearchOutboxPublisher.publishEligibleBatch()` gọi `subjects.findById` trong vòng lặp tối đa 100 | N+1 query và transaction dài ở approve/projection path; tăng DB CPU/lock time khi bulk | Bulk lookup/save theo set; `findAllById`/join fetch bounded; transaction chỉ bao quanh DB mutation, gọi Elasticsearch ngoài transaction |
| `PR-PERF-03` / `TD-017` | **MEDIUM** | Performance efficiency | `ScanReviewProjectionReadStore.java:77,94` dùng `LIMIT ? OFFSET ?`; Query normal list cũng `PageRequest` offset | Review queue sâu và list lớn phải scan bỏ qua nhiều row; latency tăng theo page, trái mục tiêu keyset của SC-01 | Chuyển projection/review API sang cursor `(observed_at/source_relative_path/id)`; giới hạn deep page và benchmark query plan |
| `PR-ARCH-01` / `TD-018` | **MEDIUM** | Architecture/quality | 58 inward imports persistence `*Entity`; ví dụ `QueryController.java:3`, `QueryProjectionService.java`, `ScanQueryService.java` | Application/controller phụ thuộc persistence model, làm boundary DTO/domain yếu và tăng blast radius migration | Tách read model/command port record; mapper ở adapter/application boundary; ưu tiên các hot path chạm contract trước |
| `PR-OPS-01` / `TD-019` | **MEDIUM** | Operational excellence | `application.yml` của Scan/Worker chứa absolute machine paths; default password `change-me-*`; profile mặc định `local`; chưa có production profile | Deploy sai môi trường hoặc lộ credential mặc định; config drift và khó audit secret | Chuyển mọi machine path/credential sang env/secret/config provider; commit `application-local.example.yml`, fail-fast khi production còn default |
| `PR-OPS-02` / `TD-020` | **MEDIUM** | Operational excellence, cost | Chỉ có dashboard Grafana; không có alert rules, SLO/error budget, runbook rollback/backup/restore trong repo | Có metric nhưng không có hành động khi backlog age, lease loss, DLT, DB saturation hoặc scan stuck; MTTR không được bảo vệ | Định nghĩa SLO/capacity budget cho SC-01 và approve; alert theo oldest age/queue age/DLT/terminal latency; viết runbook restart, replay, rollback, backup/restore |
| `PR-COST-01` / `TD-021` | **MEDIUM** | Cost, sustainability | Không thấy retention/purge policy cho outbox, processed-event, Catalog DLT; local Compose dùng volume vô thời hạn; projection chỉ cleanup generation nội bộ | Bảng/event payload/DLT tăng vô hạn, chi phí storage/WAL/backup và query degradation | Chốt retention theo audit/replay window; archive/purge idempotent, metric tuổi dữ liệu và quota; giữ DLT theo policy có owner |
| `PR-QUAL-01` / `TD-022` | **MEDIUM** | Quality/evidence | 7 Java class >250 dòng, 8 package >8 type; `ScanQueryService` 297, `ScanChunkCommitter` 283, `ScanExecutor` 272; chưa có evidence compile/formatter trong phiên | Reviewability và tốc độ thay đổi giảm; lỗi format/compile có thể tồn tại dù source chưa quá 500 dòng | Tách theo capability sau khi hardening; chạy formatter/compile/static analysis trong verification gate; ghi exception ở Plan nếu giữ façade |

## Phân tích architecture quality

### Correctness và data consistency

| Tiêu chí | Kết quả | Evidence/nhận xét |
| --- | --- | --- |
| Database ownership | **PASS (tĩnh)** | Context/ADR tách `scan_db`, `catalog_db`, `query_db`; không thấy cross-service repository import |
| Transactional outbox | **PARTIAL** | Approval/Catalog mutation enqueue outbox cùng transaction; throughput/lease và replay còn debt `TD-013/014` |
| Consumer idempotency/version | **PARTIAL** | Catalog dedupe `eventId`; Query có processed-event/version guard và retry/DLT publisher; observer/replay/runtime evidence còn thiếu (`TD-015`) |
| Checkpoint atomicity | **PASS (tĩnh)** | `ScanChunkCommitter` gộp write + conditional checkpoint trong `REQUIRES_NEW`; cần Testcontainers/race proof |
| Job state transitions | **MISSING/HIGH** | Recheck/bulk transition không fenced theo lease owner (`TD-012`) |
| API boundary | **PARTIAL** | DTO ở phần lớn controller; nhiều application type dùng persistence Entity trực tiếp (`TD-018`) |

### Safety, liveness và terminal trace

Scan happy path là:

```text
RUNNING → discovery segment checkpoint → diff → reconcile chunk checkpoint
        → FINALIZING → COMPLETED
```

Failure ở Java/DB được đưa qua `ScanExecutionFailureHandler` thành `FAILED`, và
lease expiry có handler DB conditional. Tuy nhiên hai nhánh phá vỡ guarantee:

1. **Worker restart:** `ApplicationReadyEvent` fail mọi `RUNNING`, không phân biệt
   lease còn hạn hay replica owner (`TD-010`).
2. **Filesystem dependency treo:** `Files.walkFileTree` không nhận deadline;
   interrupt không đảm bảo native/filesystem call trả quyền điều khiển (`TD-011`).

Durable recheck/bulk có state `PENDING/RUNNING/COMPLETED/FAILED`, nhưng stale
worker vẫn có thể gọi transition cuối (`TD-012`). Vì vậy workflow control tổng thể
chưa đạt terminal/fencing criterion.

### Boundary và contract

- Gateway không truy cập DB và route business chính đúng hướng.
- Route Catalog operations đang tự mâu thuẫn giữa config và contract (`TD-014`).
- ADR-005 cố ý local-only, không auth và mount drive; đây là accepted local
  design, nhưng không phải waiver production. Không có waiver người dùng cho
  session này.

## Production Readiness — sáu pillar

| AWS Well-Architected pillar | Đánh giá | Evidence và gate còn thiếu |
| --- | --- | --- |
| Operational Excellence | **PARTIAL** | ECS log, runId, tracing, dashboard có; thiếu alert/SLO/runbook/rollback và graceful shutdown policy (`TD-010`, `TD-020`) |
| Security | **MISSING — blocker** | Không authn/authz/TLS/secret provider; Nginx expose full drives và wildcard CORS (`TD-009`) |
| Reliability | **MISSING — blocker** | Scan checkpoint safety tốt nhưng restart, blocking I/O, job fencing là blocker; Query DLT observation/replay còn evidence gap (`TD-010`–`TD-015`) |
| Performance Efficiency | **PARTIAL** | Bounded scan/COPY và local benchmark; thiếu end-to-end SLO, capacity/load evidence, deep-page/keyset và outbox throughput (`TD-013`, `TD-016`, `TD-017`) |
| Cost Optimization | **MISSING** | Chưa có resource budget, Kafka/DB/WAL/retention/quota và retry cost model (`TD-013`, `TD-021`) |
| Sustainability | **PARTIAL** | Có bounded memory/set-based write giúp giảm work; chưa có lifecycle/retention/right-size evidence (`TD-021`) |

### Workflow control overlay

| Control | Kết quả |
| --- | --- |
| State machine & terminal guarantee | **PARTIAL/MISSING** — scan có terminal handler; restart/blocked I/O và durable jobs chưa đủ |
| Liveness & fencing safety | **MISSING** — scan checkpoint fenced; recheck/bulk final state chưa fenced; walker không deadline |
| Data consistency & transaction boundary | **PARTIAL** — hot path tốt; replay và long transaction cần sửa |
| Dependency resilience | **PARTIAL** — Catalog client/DB timeout có; Kafka/Query DLT và ES operation deadline/recovery chưa đủ |
| Observability & operability | **PARTIAL** — metric nền có, alert/runbook/SLO thiếu |
| Deployment/restart/graceful shutdown | **MISSING** — startup cleanup phá active run; chưa có drain/restart contract |

## SLO, capacity, cost và assumptions cần chốt

Trước khi xem xét `READY`, owner cần ghi thành contract/Plan tối thiểu:

- Scan chưa approve: volume/growth, discovery throughput, phase p50/p95/p99,
  max duration, memory bound, DB pool/WAL/IO budget và no-progress deadline.
- Approve → Catalog: event/sec, partition count, consumer concurrency, handler
  p95, outbox oldest age, retry/DLT budget và duplicate rate.
- Review/bulk: candidate cutoff semantics, batch size, job age/status SLO,
  concurrent decision behavior và restart recovery.
- Search/replay: maximum dataset, request/job timeout, index rebuild cutover,
  retention của candidate index và rollback procedure.

Số liệu trong SC-01 hiện chỉ là microbenchmark local; mọi ngưỡng trên vẫn là
**chưa xác nhận** cho tới khi có benchmark/Testcontainers/runtime evidence.

## Kế hoạch trả technical debt theo ưu tiên

### P0 — trước mọi production exposure

1. `TD-009`: chốt security/network boundary, bỏ default secret, thu hẹp Nginx
   root/alias, authn/authz và CORS.
2. `TD-010` + `TD-011`: sửa restart recovery và enforceable filesystem deadline;
   bổ sung graceful shutdown/drain policy.
3. `TD-012`: lease-owner/attempt fencing cho recheck và bulk; kiểm thử crash,
   reclaim, duplicate completion.

### P1 — trước scale/cutover SC-01

1. `TD-013`: bounded async outbox publish, timeout/in-flight/backpressure và
   capacity model.
2. `TD-014` + `TD-015`: sửa route contract, durable/idempotent replay và Query
   retry/DLT/replay runbook.
3. `TD-016` + `TD-017`: loại N+1/long transaction, chuyển review/list sang keyset,
   đo query plan và Kafka/Catalog load.

### P2 — hardening vận hành dài hạn

1. `TD-018` + `TD-022`: tách DTO/port khỏi persistence entity, giảm façade/package
   quá tải, formatter/compile/static-analysis gate.
2. `TD-019` + `TD-020`: chuẩn hóa config profile/secret và alert/SLO/runbook.
3. `TD-021`: retention/archive/quota cho outbox, processed events, DLT và index.

## Verification gate chưa chạy trong session

Các lệnh sau là follow-up có owner, không phải bằng chứng đã pass:

- Maven validate/test bằng IntelliJ Project SDK `corretto-25`, Spotless và
  `git diff --check`.
- Testcontainers + Flyway cho Scan/Catalog/Query; kiểm tra unique index,
  dropped FK, query plan và migration conflict.
- Race test: two workers/replicas, lease expiry, stale completion, process
  restart, rolling deployment và graceful shutdown.
- SC-01 load profile tách discovery, reconciliation, approve/outbox/Kafka,
  Catalog consumer và Query projection; thu phase timing + resource profile.
- Poison event/DLT/replay, duplicate/out-of-order, ES/Redis/Kafka outage và
  backup/restore/rollback drill.

## Accepted risks / waivers

**Không có waiver production được người dùng phê duyệt trong review này.**

ADR-005 và Compose single-node/plaintext/full-drive được xem là giới hạn
**local study** có chủ đích; chúng vẫn là blocker nếu dùng cùng cấu hình để
expose production hoặc mạng không tin cậy.

## Source of truth

- Backlog cô đọng: [`docs/TECHNICAL_DEBT.md`](../TECHNICAL_DEBT.md)
- Snapshot trạng thái/gate: [`docs/STATUS.md`](../STATUS.md)
- Kiến trúc/rules: [`docs/architecture/01-SUMMARY.md`](../architecture/01-SUMMARY.md),
  [`docs/architecture/03-CODING_RULES.md`](../architecture/03-CODING_RULES.md)
- Owner scan: [`apps/scan-service/CONTEXT.md`](../../apps/scan-service/CONTEXT.md)
- Workload evidence: [SC-01 performance/cloud note](../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/06-performance-and-cloud-scaling.md)
- ADR delivery: [`docs/adr/ADR-005-nginx-direct-media-delivery.md`](../adr/ADR-005-nginx-direct-media-delivery.md)
