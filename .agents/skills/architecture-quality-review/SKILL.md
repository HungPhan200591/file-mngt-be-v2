---
name: architecture-quality-review
description: Review code changes and feature work for PRD readiness, architectural boundary integrity, ownership, contract correctness, maintainability, and implementation quality in the Backend V2 study project. Use when reviewing a diff, commit, pull request, feature folder, or newly implemented use case; especially when changes touch service boundaries, database ownership, REST/Kafka contracts, transactions, filesystem scanning, or application/domain/adapter layering.
---

# Architecture Quality Review

Đánh giá một thay đổi từ góc nhìn có thể bàn giao theo PRD và đúng kiến trúc Backend V2. Skill này kết hợp blocking review (sai boundary, sai ownership, sai contract, mất invariant) với quality review (độ rõ ràng, cohesion, maintainability, hiệu năng hợp lý), nhưng không biến một dự án study thành production gate.

## Nguyên tắc

- Review bằng bằng chứng trong diff và source-of-truth; không suy đoán thay cho code hoặc tài liệu.
- Một finding phải chỉ rõ file/dòng hoặc symbol, impact, điều kiện xảy ra và hướng sửa.
- Ưu tiên boundary và invariant hơn style. Không tạo finding chỉ vì khác sở thích cá nhân.
- `Critical`/`High` chỉ dùng khi thay đổi không đạt PRD, phá ownership/contract, làm sai dữ liệu nghiệp vụ hoặc tạo lỗi không thể tự phục hồi trong flow chính.
- Testing, migration rollback, load test và observability là evidence/follow-up theo rủi ro. Với study project, có thể ghi nhận gap mà không block nếu data có thể xóa/tạo lại và boundary vẫn an toàn.
- **Cho phép intentional behavior change**: nếu Brief/PRD nói hành vi mới thay thế hành vi cũ, review không yêu cầu backward compatibility, giữ hai behavior, compatibility adapter, deprecation period hoặc migration cho behavior cũ. Chỉ flag khi code vô tình chạy song song hai semantics, còn stale branch/caller gây mâu thuẫn, hoặc behavior mới tự mâu thuẫn với acceptance criteria. Chỉ review compatibility khi người dùng yêu cầu rõ hoặc feature brief đặt nó làm acceptance criteria.
- Không tự sửa code trong lúc review trừ khi người dùng yêu cầu; không chạy migration thật, import dữ liệu, reset repo, khởi động service hoặc commit/push.

## Workflow

### 1. Xác định input và phạm vi

Nhận một trong các input: commit/diff, danh sách file, feature folder hoặc path code. Xác định:

- mục tiêu PRD/acceptance criteria và phần out of scope;
- service owner, database owner, API/event contract và các invariant liên quan;
- file mới/sửa/xóa, dependency trực tiếp và thay đổi runtime;
- loại review: full review mặc định; quality-only chỉ khi người dùng yêu cầu.

Đọc tối thiểu `docs/architecture/01-SUMMARY.md`, `docs/architecture/03-CODING_RULES.md`, `docs/STATUS.md` và `apps/<service>/CONTEXT.md` của service bị chạm. Khi feature có brief/design/plan, đọc các file đó trước khi kết luận PRD readiness. Chỉ đọc ADR/contract/migration docs khi diff chạm boundary tương ứng.

Nếu review commit lớn, đọc `git show --stat --name-status` và patch từ file export để tránh terminal truncation. Không cần bắt chước quy trình export report của workflow livestream cũ nếu người dùng không yêu cầu; vẫn phải ghi lại commit/hash hoặc phạm vi đã review.

### 2. Kiểm tra PRD readiness

Đối chiếu từng acceptance criterion với implementation và đánh dấu `PASS`, `PARTIAL` hoặc `MISSING`:

- data model, constraint, index và persistence behavior;
- happy path, failure path, retry/idempotency và transaction boundary;
- contract và invariant của feature mới (không mặc định giữ contract/behavior cũ nếu feature chủ động thay thế chúng);
- out-of-scope không bị kéo vào feature;
- tài liệu owner (brief/design/plan/contract/ADR) có khớp implementation.

Không coi “có test” là bằng chứng duy nhất. Có thể dùng code path, constraint, query, transaction annotation, log/metric và tài liệu làm evidence; ghi rõ evidence còn thiếu.

### 3. Kiểm tra ranh giới kiến trúc

Kiểm tra theo thứ tự:

1. Service chỉ truy cập database của chính nó; không import entity/repository/schema của service khác.
2. Luồng mặc định là `adapter.in -> application -> domain`; adapter làm I/O/map, không quyết định nghiệp vụ.
3. `domain` không phụ thuộc Spring, HTTP, JPA, Kafka hoặc filesystem.
4. Transaction nằm ở application use case; side effect cần invariant được commit cùng nhau.
5. REST/Kafka boundary dùng DTO/event version phù hợp; không trả persistence entity; consumer có idempotency khi cần.
6. PostgreSQL là source of truth; Redis chỉ cache/read optimization; Kafka không thay thế HTTP call bắt buộc.
7. Không tạo service/package/port/utility chỉ để đủ pattern; mỗi abstraction phải che giấu dependency hoặc policy thực sự.
8. Với scan-service, giữ các invariant: lấy `RegistrySnapshot` trước `scan_run`, preview không side effect, parse mơ hồ tạo issue, approval ghi item và outbox cùng transaction, log không lộ absolute root.

### 4. Kiểm tra code quality

Tập trung vào lỗi làm tăng chi phí thay đổi hoặc che giấu hành vi:

- method/class quá dài hoặc trộn orchestration, validation, mapping và I/O;
- tên, type và state transition không diễn đạt nghiệp vụ;
- magic string/number, raw map/string trong core logic, null collection;
- duplicate query/I/O, N+1, batch sai kích thước, transaction quá rộng hoặc thiếu;
- catch/nuốt exception, log thiếu context hoặc lộ path/secret;
- dead code, config/version không theo convention;
- file vượt 500 dòng hoặc vi phạm ngưỡng coding rules mà không có lý do trong Plan.

Không block chỉ vì format, thiếu test cho nhánh không quan trọng, hoặc migration chưa có rollback trong môi trường study nếu feature plan đã chấp nhận reset data. Tuy nhiên vẫn ghi thành `Low`/follow-up khi nó làm giảm khả năng học, debug hoặc mở rộng.

### 5. Kết luận và báo cáo

Phân loại severity:

- `Critical`: không đạt PRD hoặc phá boundary/invariant nghiêm trọng, cần dừng bàn giao.
- `High`: lỗi correctness, ownership, contract, transaction hoặc security có khả năng xảy ra trong flow chính.
- `Medium`: thiếu khả năng bảo trì, failure handling, hiệu năng hoặc tài liệu làm feature khó vận hành/mở rộng.
- `Low`: style, clarity hoặc debt nhỏ, không ảnh hưởng hành vi hiện tại.

Verdict:

- `READY`: không còn Critical/High; acceptance criteria đã đủ evidence.
- `CONDITIONAL`: còn Medium/Low hoặc evidence chưa đầy đủ nhưng không phá boundary/invariant.
- `NOT READY`: có Critical/High, acceptance criterion bị thiếu, hoặc cần quyết định kiến trúc/contract trước khi merge.

Dùng format sau, viết ngắn nhưng đủ bằng chứng:

```text
Review scope: <commit/diff/feature>
Verdict: READY | CONDITIONAL | NOT READY

PRD readiness:
- [PASS|PARTIAL|MISSING] <criterion> — Evidence: <file/symbol>

Findings:
- [Critical|High|Medium|Low] <title>
  Location: <file:line or symbol>
  Impact: <what can go wrong>
  Recommendation: <smallest useful fix>

Architecture boundary summary:
- Service/database ownership: <result>
- Layer/dependency direction: <result>
- REST/Kafka/transaction contract: <result or N/A>

Gaps / follow-ups:
- <tests, docs, observability, migration or debt; state why non-blocking when applicable>
```

Nếu không có finding, nói rõ `Không phát hiện lỗi trong phạm vi đã review` và nêu residual risk/evidence gap. Không tự mở rộng sang refactor hoặc feature tiếp theo.

## Tài liệu cần nạp theo điều kiện

- Đổi REST, Kafka, outbox, database ownership hoặc chạm từ hai service: đọc `$cross-service-contract` và contract/ADR owner trước khi review.
- Feature mới hoặc đổi nghiệp vụ: đọc `$adlc-feature-delivery` để hiểu Brief/Design/Plan và gate bàn giao.
- Chỉ cleanup giữ nguyên behavior: đọc `$refactor-spring-service` và đánh giá theo debt đã đăng ký.
- Đổi Mermaid: đọc `$mermaid-styling`.
- Chạm API/config/library version: dùng `$find-docs` trước khi đánh giá tính đúng của API.
