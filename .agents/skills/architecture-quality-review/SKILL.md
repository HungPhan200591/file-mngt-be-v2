---
name: architecture-quality-review
description: Review code changes and feature work for PRD readiness, architectural boundaries, ownership, contracts, maintainability, resilience, and liveness in Backend V2. Use for diffs, commits, pull requests, feature folders, or implemented use cases; especially service/database boundaries, REST/Kafka, transactions, async workers, leases, schedulers, blocking I/O, filesystem scanning, or application/domain/adapter layering.
---

# Architecture Quality Review

Đánh giá thay đổi có thể bàn giao theo PRD, đúng kiến trúc và không che giấu failure mode khiến flow chính sai dữ liệu hoặc không thể kết thúc.

## Nguyên tắc

- Review bằng bằng chứng trong diff, dependency trực tiếp và source of truth; tài liệu im lặng không phải bằng chứng một rủi ro đã được chấp nhận.
- Mỗi finding phải nêu file/dòng hoặc symbol, điều kiện xảy ra, impact và hướng sửa nhỏ nhất hữu ích.
- Ưu tiên boundary, correctness, safety và liveness hơn style; không tạo finding theo sở thích cá nhân.
- Testing/load test/observability là evidence theo rủi ro, không thay thế việc trace code path và state transition.
- Intentional behavior change không cần giữ behavior cũ, nhưng phải nhất quán với acceptance criteria và không để stale branch/caller chạy semantics cũ.
- Không tự sửa code khi chỉ được yêu cầu review; không tự chạy test/build, migration/import thật, reset, service, Docker, commit hay push.
- Chỉ coi rủi ro là được bỏ qua khi người dùng chủ động chấp nhận rõ trong yêu cầu/review hiện tại; ghi lại waiver và residual risk. `Out of scope`, code cũ hoặc Brief im lặng không phải waiver.

## Workflow

### 1. Xác định phạm vi

- Ghi review scope: commit/diff/file/feature, mục tiêu, acceptance criteria, out of scope và loại full/quality-only.
- Xác định service/database owner, API/event contract, invariant, file thay đổi, dependency trực tiếp và runtime path.
- Đọc tối thiểu `docs/architecture/01-SUMMARY.md`, `docs/architecture/03-CODING_RULES.md`, `docs/STATUS.md`, đúng `apps/<service>/CONTEXT.md`; đọc Brief/Design/Plan nếu có.
- Chỉ đọc ADR/contract/migration khi chạm boundary tương ứng. Với commit lớn, kiểm tra stat/name-status và patch đầy đủ, tránh kết luận từ diff bị truncate.

### 2. Kiểm tra PRD readiness

Đánh dấu từng criterion `PASS`, `PARTIAL` hoặc `MISSING` theo bằng chứng:

- data model, constraint, index và persistence behavior;
- happy path, failure path, retry/idempotency, transaction và compensation;
- contract/invariant của behavior mới và caller/branch liên quan;
- tài liệu owner khớp implementation, out-of-scope không bị kéo vào.

Nếu code tạo failure mode quan trọng mà PRD không nói tới, tạo finding và yêu cầu bổ sung quyết định; không tự suy ra rằng PRD đã cho phép.

### 3. Gate bắt buộc về liveness và resilience

Áp dụng khi flow có async/background worker, lease/heartbeat/lock, scheduler, retry, blocking filesystem/network/database I/O hoặc trạng thái trung gian dài hạn:

1. Tách rõ **safety** (worker stale không được commit sai) và **liveness** (flow không kẹt mãi, eventually về terminal); phải có evidence cho cả hai.
2. Trace state machine từ mọi trạng thái non-terminal tới `COMPLETED`, `FAILED`, `CANCELLED` hoặc trạng thái terminal tương đương, kể cả worker chết, dependency treo và service restart.
3. Với từng blocking operation, xác định deadline/timeout, khả năng cancel, exception propagation và hành động recovery. Timeout chỉ ghi trong tài liệu nhưng không được enforce là `MISSING`.
4. Xác định cơ chế thu hồi chủ động khi mất progress: watchdog/reaper hoặc cơ chế tương đương. Cleanup chỉ chạy khi request mới đến, polling chỉ đọc trạng thái, hay kiểm tra ở commit kế tiếp không đủ nếu operation có thể không trả control.
5. Kiểm tra fencing sau timeout/cancel để worker cũ không thể commit muộn; kiểm tra cleanup idempotent và race giữa reaper với worker.
6. Lập time budget khi có lease: `operation timeout < no-progress/lease deadline < total run deadline`; nêu rõ cadence heartbeat/reaper và sai số cho phép.
7. Thiếu cơ chế khiến flow chính có thể `RUNNING`/block vô hạn là `High` và `NOT READY`. Chỉ thiếu evidence định lượng nhưng đã có đường thoát enforce được là ít nhất `Medium` và `CONDITIONAL`.

Không hạ severity hoặc bỏ finding nếu chưa có waiver chủ động của người dùng. Nếu có waiver, vẫn ghi `Accepted risks / waivers`, phạm vi và hậu quả; không biến waiver thành invariant dùng cho review sau.

### 4. Kiểm tra ranh giới kiến trúc

1. Service chỉ truy cập database của mình; không import entity/repository/schema service khác.
2. Luồng mặc định `adapter.in -> application -> domain`; adapter làm I/O/map, domain không phụ thuộc Spring/HTTP/JPA/Kafka/filesystem.
3. Transaction ở application use case; side effect cùng invariant commit nguyên tử hoặc có consistency strategy rõ.
4. REST/Kafka dùng DTO/event version phù hợp; không trả persistence entity; consumer idempotent khi cần.
5. PostgreSQL là source of truth; Redis chỉ tối ưu read/cache; Kafka không thay HTTP call bắt buộc.
6. Abstraction/service/package/port phải che giấu dependency hoặc policy thực sự, không chỉ tồn tại để đủ pattern.
7. Với scan-service: lấy `RegistrySnapshot` trước `scan_run`, preview không side effect, parse mơ hồ tạo issue, approval ghi item/outbox cùng transaction, log không lộ absolute root.

### 5. Kiểm tra code quality

- method/class trộn orchestration, validation, mapping và I/O hoặc vượt ngưỡng coding rules;
- tên/type/state transition không diễn đạt semantics; magic string/number, raw map/string, null collection;
- duplicate query/I/O, N+1, batch không bounded, transaction quá rộng/thiếu;
- catch/nuốt exception, mất interrupt/cancellation, log thiếu context hoặc lộ path/secret;
- dead code, config/version sai convention; file source vượt 500 dòng mà không có ngoại lệ trong Plan.

Format, test nhánh không quan trọng hoặc rollback migration của dữ liệu study có thể là Low/follow-up, nhưng không được dùng lý do “study project” để bỏ qua correctness, liveness hoặc khả năng tự phục hồi của flow chính.

### 6. Kết luận

- `Critical`: không đạt PRD hoặc phá boundary/invariant nghiêm trọng.
- `High`: lỗi correctness, ownership, contract, transaction, security hoặc flow chính có thể không kết thúc/tự phục hồi.
- `Medium`: gap maintainability, failure handling, performance, evidence hoặc tài liệu làm feature khó vận hành.
- `Low`: clarity/style/debt nhỏ không ảnh hưởng hành vi hiện tại.
- `READY`: không còn Critical/High và đủ evidence; `CONDITIONAL`: còn Medium/Low/evidence gap; `NOT READY`: có Critical/High hoặc criterion thiếu.

```text
Review scope: <commit/diff/feature>
Verdict: READY | CONDITIONAL | NOT READY
PRD readiness: - [PASS|PARTIAL|MISSING] <criterion> — Evidence: <file/symbol>
Findings: - [severity] <title>; Location; Condition/Impact; Recommendation
Safety & liveness: <terminal-state trace, blocking deadlines, recovery/fencing, time budget hoặc N/A>
Architecture: <ownership; layer direction; REST/Kafka/transaction>
Accepted risks / waivers: <user-approved risk hoặc None>
Gaps / follow-ups: <evidence/tests/docs/observability và lý do non-blocking>
```

Nếu không có finding, nói rõ và vẫn nêu residual risk/evidence gap. Không tự mở rộng sang implementation.

## Tài liệu theo điều kiện

- Đổi REST/Kafka/outbox/database ownership hoặc chạm hai service: dùng `$cross-service-contract` và đọc owner contract/ADR.
- Feature mới/đổi nghiệp vụ: dùng `$adlc-feature-delivery`; cleanup giữ behavior: dùng `$refactor-spring-service`.
- Đổi Mermaid: dùng `$mermaid-styling`; chạm API/config/library version: dùng `$find-docs`.
