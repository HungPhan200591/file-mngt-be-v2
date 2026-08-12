---
name: architecture-quality-review
description: Review diffs and feature work in Backend V2 for correctness, boundaries, contracts, maintainability, resilience, and liveness. Use especially for async workers, leases, schedulers, blocking I/O, filesystem scanning, REST/Kafka, transactions, and service/database boundaries.
---

# Architecture Quality Review

Đánh giá thay đổi có đúng kiến trúc và không che giấu failure mode làm flow chính sai dữ liệu hoặc không thể kết thúc. Đây là review thường dùng cho diff/feature; full Production Readiness review là scope riêng.

## Nguyên tắc

- Dựa trên evidence trong diff, dependency trực tiếp và source of truth; mỗi finding nêu location, điều kiện, impact và hướng sửa nhỏ nhất.
- Ưu tiên correctness, safety, liveness và boundary hơn style. Không tự sửa code khi chỉ được yêu cầu review; không tự chạy test/build, migration, service, Docker, commit hay push.
- Hành vi thay đổi có chủ đích vẫn phải khớp acceptance criteria, caller liên quan và không để semantics cũ chạy sót.
- Rủi ro chỉ được coi là waived khi người dùng chấp nhận rõ trong review hiện tại; ghi residual risk, không biến waiver thành rule chung.

## Workflow

### 1. Xác định scope

- Ghi scope, mục tiêu, acceptance criteria, out-of-scope và loại review.
- Xác định service/database owner, API/event contract, invariant, runtime path và dependency trực tiếp.
- Đọc tối thiểu `docs/architecture/01-SUMMARY.md`, `docs/architecture/03-CODING_RULES.md`, `docs/STATUS.md`, đúng `apps/<service>/CONTEXT.md`; chỉ đọc ADR/contract/migration khi chạm boundary đó.

### 2. Correctness và behavior

Đánh dấu `PASS`, `PARTIAL` hoặc `MISSING` cho data model/index, happy/failure path, retry/idempotency, transaction/compensation, contract/invariant và tài liệu owner. PRD im lặng về failure mode quan trọng là finding, không phải ngầm chấp nhận.

Với mỗi entry point mới hoặc sửa, bắt buộc thực hiện control-flow partition review:

1. Liệt kê các partition đầu vào và trạng thái hệ thống có thể làm control flow đổi nhánh.
2. Trace từng partition từ entry đến return hoặc terminal state.
3. Đối chiếu side effect dự kiến và thực tế: DB, network, event, metric và log.
4. Kiểm tra tối thiểu happy path, no-op/boundary, partial failure, total failure, retry/re-entry và concurrent
   execution khi áp dụng.
5. Không kết luận `READY` nếu còn nhánh chưa trace hoặc side effect không tương ứng với công việc đã hoàn thành.

Với batch, scheduler, poller và outbox publisher, cardinality là một partition bắt buộc: `0`, `1` và đầy batch.
Batch `0` là **no-op/boundary path**, không phải failure. Khi claim/read trả collection rỗng, mặc định phải return
trước dispatch, persistence, success metric và success log; chỉ tiếp tục nếu có housekeeping được mô tả rõ. Không
log `INFO` theo polling cycle rỗng; nếu idle observation thật sự cần thiết thì dùng metric hoặc log rate-limited/debug.
Review phải tìm evidence rằng empty batch không gọi downstream, không ghi DB và không báo thành công giả.

### 3. Gate liveness và resilience

Áp dụng khi có background worker, lease/heartbeat/lock, scheduler, retry, blocking filesystem/network/database I/O hoặc trạng thái trung gian dài hạn:

1. Tách **safety** (worker stale không commit sai) khỏi **liveness** (flow luôn về terminal); phải có evidence cho cả hai.
2. Trace mọi non-terminal state đến `COMPLETED`, `FAILED`, `CANCELLED` hoặc terminal tương đương, kể cả worker chết, dependency treo và service restart.
3. Với từng I/O blocking, kiểm tra timeout/deadline được enforce, cancellation, exception propagation và recovery; timeout chỉ ghi tài liệu là `MISSING`.
4. Kiểm tra cơ chế thu hồi progress mất tích: watchdog/reaper hoặc tương đương. Cleanup chỉ khi request mới đến/polling/commit kế tiếp không đủ nếu operation có thể không trả control.
5. Kiểm tra fencing/conditional update và cleanup idempotent để worker cũ không commit sau timeout/cancel.
6. Khi có lease, kiểm tra ngân sách `operation timeout < no-progress/lease deadline < total run deadline`, cadence heartbeat và sai số.
7. **Với async worker, timer hoặc scheduler mới/sửa:** kiểm tra policy graceful shutdown: dừng nhận việc mới, drain/cancel theo policy, recovery khi restart. Không mặc định gọi “release lease” trong `@PreDestroy`.

Thiếu đường thoát có thể làm `RUNNING`/block vô hạn là `High` và `NOT READY`.

### 4. Gate boundary

1. Service chỉ truy cập database của mình; adapter.in → application → domain; domain không phụ thuộc Spring/HTTP/JPA/Kafka/filesystem.
2. Transaction nằm ở application use case; side effect cùng invariant atomic hoặc có consistency strategy rõ.
3. REST/Kafka dùng DTO/event phù hợp, không trả entity persistence; consumer idempotent khi cần.
4. PostgreSQL là source of truth; Redis chỉ cache/read optimization; Kafka không thay HTTP bắt buộc.
5. Abstraction/package/port phải che dependency hoặc policy thật, không chỉ đủ pattern.

### 5. Code quality và operability cơ bản

- Tìm orchestration/validation/mapping/I/O trộn lẫn, vượt coding rules, N+1, batch không bounded, transaction quá rộng, catch nuốt exception/cancellation, log lộ path/secret và dead code.
- **Với async boundary mới/sửa:** kiểm tra định danh durable như `runId` được log/truyền rõ; có log, metric hoặc health/progress signal tương xứng. Không giả định request MDC tự đi qua thread background.
- File source vượt 500 dòng không có ngoại lệ trong Plan là finding.

### 6. Kết luận

- `Critical`: vỡ boundary/invariant nghiêm trọng. `High`: correctness, ownership, contract, transaction, security hoặc flow chính có thể không kết thúc/tự phục hồi.
- `Medium`: maintainability, handling, performance, evidence hoặc vận hành khó. `Low`: clarity/style/debt nhỏ.
- `READY`: không còn Critical/High và đủ evidence; `CONDITIONAL`: còn Medium/Low/evidence gap; `NOT READY`: có Critical/High hoặc criterion thiếu.

```text
Review scope: <commit/diff/feature>
Verdict: READY | CONDITIONAL | NOT READY
Readiness: [PASS|PARTIAL|MISSING] <criterion> — Evidence: <file/symbol>
Findings: [severity] <title>; Location; Condition/Impact; Recommendation
Safety & liveness: <terminal trace, deadlines, recovery/fencing, shutdown/restart or N/A>
Architecture: <ownership; layers; REST/Kafka/transaction>
Accepted risks / waivers: <user-approved risk or None>
Gaps / follow-ups: <evidence/docs/observability>
```

## Routing theo điều kiện

- Đổi REST/Kafka/outbox/database ownership hoặc chạm hai service: `$cross-service-contract`.
- Feature mới/đổi nghiệp vụ: `$adlc-feature-delivery`; cleanup giữ behavior: `$refactor-spring-service`.
- Release/cutover, service mới, worker/scheduler/message pipeline mới, major migration, hoặc người dùng yêu cầu đánh giá toàn diện sáu trụ cột: `$production-readiness-review`.
- Đổi Mermaid: `$mermaid-styling`; chạm API/config/library version: `$find-docs`.
