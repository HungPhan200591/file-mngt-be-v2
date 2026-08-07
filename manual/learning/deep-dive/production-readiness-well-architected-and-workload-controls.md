# Production Readiness: Khung chuẩn và control áp dụng cho toàn Backend V2

> Mục tiêu học: phân biệt framework công bố bởi ngành với checklist nội bộ; biết đánh giá mọi loại workload của Backend V2 — HTTP API, ghi dữ liệu, Kafka consumer, background worker, read model và migration — trước khi đưa vào vận hành.
>
> Prerequisite: HTTP, database transaction, process/thread, event/message và timeout cơ bản.

## Bản chất trong một câu

**Production Readiness không có một bộ sáu trụ cột chính thức toàn thế giới.** Ta dùng Well-Architected làm khung quality chuẩn, rồi chọn control cụ thể theo rủi ro của từng workload.

Keyword spine: **quality pillars → workload risk → control → evidence → release decision**.

## D0 — Vấn đề cần giải quyết

Một feature có thể đúng ở happy path nhưng chưa vận hành được: Gateway trả lỗi không truy vết được, Catalog ghi DB nhưng event không phát, Kafka consumer bị poison message chặn partition, Media Worker rơi job khi restart, Query read model bị stale, hay migration làm version cũ/mới vỡ nhau. Production Readiness là review có bằng chứng cho các rủi ro đó trước release.

Không có một “chuẩn xã hội” độc quyền cho mọi loại hệ thống. Google SRE mô tả Production Readiness Review (PRR) như quy trình/checklist theo chi tiết từng service, không áp một danh sách sáu mục phổ quát. [Google SRE PRR](https://sre.google/sre-book/evolving-sre-engagement-model/)

## D1 — Khung chuẩn nào đang tồn tại?

AWS Well-Architected công bố sáu pillar: **Operational Excellence, Security, Reliability, Performance Efficiency, Cost Optimization, Sustainability**. Đây là framework best practice để review workload, không phải chứng nhận pháp lý hoặc chuẩn ISO. [AWS Well-Architected](https://docs.aws.amazon.com/wellarchitected/latest/framework/the-pillars-of-the-framework.html)

Azure Well-Architected dùng năm pillar: Reliability, Security, Cost Optimization, Operational Excellence và Performance Efficiency; không có Sustainability như một pillar riêng. [Microsoft Azure Well-Architected](https://learn.microsoft.com/en-us/azure/well-architected/what-is-well-architected-framework)

NIST SP 800-160 cung cấp principles cho engineering hệ thống trustworthy/secure và resilience, đặc biệt hữu ích cho góc Security, nhưng không định nghĩa “sáu pillar Production Readiness”. [NIST SP 800-160](https://csrc.nist.gov/pubs/sp/800/160/v1/r1/final)

Vì vậy, khi nói “sáu pillar chuẩn” trong tài liệu này, ta chỉ rõ đó là **sáu pillar của AWS Well-Architected**.

| Pillar AWS | Câu hỏi ngắn | Bằng chứng tiêu biểu |
|---|---|---|
| Operational Excellence | Có vận hành, quan sát, cải tiến được không? | runbook, logs, metrics, alert, rollback |
| Security | Dữ liệu, quyền và tài sản có được bảo vệ không? | authz, secrets, audit, encryption, threat controls |
| Reliability | Gặp lỗi có vẫn phục hồi/đúng không? | state/retry/recovery, idempotency, failure tests |
| Performance Efficiency | Có đạt tải/SLO hiệu quả không? | capacity model, load evidence, saturation controls |
| Cost Optimization | Có dùng tài nguyên theo giá trị không? | budget, utilization, retention, waste controls |
| Sustainability | Có giảm resource/energy không cần thiết không? | right-sizing, lifecycle, efficiency trend |

## D2 — Hai tầng review cho toàn dự án

```text
Production Readiness
├─ Tầng 1: 6 pillar AWS Well-Architected (mọi workload)
└─ Tầng 2: control theo loại workload và failure mode
   ├─ HTTP API / synchronous request
   ├─ DB write + domain event
   ├─ Kafka consumer / projection
   ├─ background worker / long-running job
   └─ migration / deploy / shutdown
```

Tầng 1 ngăn bỏ quên Security, capacity hay resource cost. Tầng 2 không cạnh tranh với framework; nó chuyển pillar thành kiểm tra cụ thể cho từng đường chạy.

| Control tái sử dụng | Pillar chính | Áp dụng điển hình |
|---|---|---|
| State machine & terminal guarantee | Reliability | worker, approval flow, consumer retry, migration |
| Timeout, deadline, retry, back-pressure | Reliability, Performance | HTTP, DB, filesystem, Kafka, external dependency |
| Idempotency, transaction boundary, outbox | Reliability, Security | API write, Catalog event, consumer, projection |
| Fencing/ownership/conditional update | Reliability | lease, scheduled work, competing consumer, stale update |
| Observability & operability | Operational Excellence | tất cả request, event, job, deploy |
| Compatibility, recovery & graceful shutdown | Operational Excellence, Reliability | mọi service, migration, rolling deploy |

## D3 — Sáu pillar chuẩn, diễn giải cho Backend V2

### 1. Operational Excellence

Khả năng **chạy và cải tiến** hệ thống: log, metric, alert, dashboard, runbook, change/rollback và quy trình xử lý incident.

Ví dụ theo dự án: Gateway tạo/truyền correlation ID; service xử lý request hoặc event log identity bền vững (`requestId`, `eventId`, `jobId`); Media Worker có progress/failure signal; Query Service có chỉ số projection lag. Các ví dụ này là mục tiêu kiểm tra, không khẳng định mọi capability đã được triển khai.

### 2. Security

Bảo vệ confidentiality, integrity và availability: xác thực, phân quyền, least privilege, secrets, encryption, audit và không lộ dữ liệu nhạy cảm.

Ví dụ: Gateway không trả stack trace/secret ra client; mỗi service chỉ có DB user của DB nó sở hữu; event và log không lộ path vật lý hoặc metadata riêng tư; endpoint thay đổi Catalog phải có authorization phù hợp khi feature yêu cầu.

### 3. Reliability

Khả năng workload thực hiện đúng chức năng nhất quán và phục hồi khi lỗi. Trọng tâm là state durable, timeout, recovery, idempotency, transaction boundary và stale-work fencing khi có concurrency.

Ví dụ: Catalog ghi business data và outbox trong cùng transaction; consumer nhận event trùng không tạo projection trùng; job Media Worker có terminal result khi tool xử lý lỗi; Scan là một ví dụ khác của cùng nguyên tắc, không phải phạm vi duy nhất.

### 4. Performance Efficiency

Không chỉ là “nhanh”: dùng compute, I/O, database và memory hiệu quả để đạt SLO khi tải/dữ liệu thay đổi.

Ví dụ: Gateway cần giới hạn request/in-flight work; Catalog cần index và query bounded; Query Service cần read model hợp với filter/gallery; Media Worker cần giới hạn concurrency CPU; Scan cần capacity model khi file tăng từ 1M lên 10M. Mỗi service có bottleneck khác nhau, nên không có một benchmark thay thế cho tất cả.

### 5. Cost Optimization

Dùng tài nguyên để tạo business value với chi phí hợp lý, không phải ép rẻ làm giảm reliability.

Ví dụ: Kafka retention, retry vô hạn, polling dày, read model dư thừa, thumbnail/GIF tạo lại nhiều lần và query thiếu index đều có thể làm tăng storage/CPU/DB I/O. Review cost hỏi “chi phí tăng theo cái gì, có budget/retention không?”

### 6. Sustainability

Giảm năng lượng và tài nguyên cần thiết mà vẫn đạt yêu cầu. Nó liên quan performance/cost nhưng AWS tách riêng để không chỉ tối ưu tiền ngắn hạn.

Ví dụ: cache hợp lý thay vì render media lặp, xóa dữ liệu tạm theo lifecycle, batch bounded thay vì tạo object/DB work vô ích. Với local/study project, đo carbon có thể `N/A`, nhưng tránh lãng phí CPU/DB/IO vẫn là evidence hợp lệ.

## D4 — Control theo loại workload

### A. HTTP API và Gateway

Luồng điển hình: client → Gateway → service → DB/response. Review timeout ở dependency, request size/rate limit, authn/authz, validation, idempotency cho command quan trọng, correlation ID, error contract an toàn và back-pressure khi downstream chậm.

Ví dụ dễ hiểu: một endpoint upload hoặc search có thể làm CPU/DB cạn dù response vẫn đúng. Readiness không chỉ hỏi “HTTP 200 chưa?” mà hỏi “khi 1.000 request cùng đến, điều gì bị giới hạn và client nhận lỗi nào?”.

### B. DB write và domain event

Luồng điển hình: command → transaction → business data + outbox → publisher → Kafka. Invariant cùng thay đổi phải nằm trong một transaction local; outbox tránh khoảng hở “DB đã commit nhưng process chết trước publish”. Consumer phải idempotent vì at-least-once delivery/duplicate là assumption phổ biến.

Ví dụ dễ hiểu: Catalog tạo asset thành công nhưng Query không được báo event thì gallery stale. Outbox biến việc “cố gửi sau” thành dữ liệu durable để publisher retry.

### C. Kafka consumer, projection và read model

Luồng điển hình: event → validate/version → deduplicate → transaction update projection → acknowledge. Review schema compatibility, idempotency, ordering key, retry budget, DLT/poison-message policy, consumer lag, replay/rebuild và quyền sở hữu read model.

Ví dụ dễ hiểu: một event lỗi parse retry vô hạn có thể chặn partition, khiến event hợp lệ phía sau không được xử lý. DLT không sửa event; nó tách event cần điều tra để luồng còn lại tiến lên.

### D. Background worker và long-running job

Luồng điển hình: create durable job → claim/execute → checkpoint → terminal state. Review state machine, deadline, cancellation, progress, resource bound, restart recovery; dùng lease/fencing khi có thể tồn tại worker stale hoặc cạnh tranh.

Ví dụ: Media Worker tạo thumbnail và Scan duyệt filesystem đều là background job, nhưng timeout/batch/SLO khác nhau. Control giống nhau ở mức nguyên tắc, không được copy con số lease/timeout của scan sang media job.

### E. Migration, deploy và shutdown

Rolling deployment có thể chạy code cũ và mới song song. Migration an toàn thường expand-contract: thêm cấu trúc tương thích trước, deploy code đọc được cả hai dạng, chỉ xóa phần cũ ở release sau. Flyway versioned migration không cần bị gọi chung là “idempotent”; trọng tâm là backward compatibility.

Shutdown cần policy: stop intake, drain transaction ngắn, cancel/mark work dài theo policy, sau đó restart recovery xử lý phần dang dở. Không “release lock/lease” mù quáng nếu worker cũ còn có thể commit.

## Guarantees, best effort và assumptions

| Loại | Ví dụ | Cách ghi đúng |
|---|---|---|
| Guarantee | Conditional update `WHERE status = RUNNING` không cho stale transition ghi đè nếu mọi writer dùng nó | nêu invariant và nơi enforce |
| Best effort | Graceful shutdown cố drain transaction ngắn trước deadline process | nêu timeout và fallback khi không drain kịp |
| Assumption | Kafka/event có thể duplicate; external API có thể timeout | nêu consumer/idempotency/retry policy bảo vệ assumption |

## Decision table

| Tình huống | Review cần dùng |
|---|---|
| Diff nhỏ, bug fix cục bộ | `architecture-quality-review` |
| API write, consumer, worker hoặc migration trước release | architecture review + control theo workload liên quan |
| Release/cutover, service/pipeline mới, thay đổi capacity/SLO lớn | `production-readiness-review`: đủ 6 pillar AWS và control liên quan |
| Local study, không có cloud bill/carbon metric | ghi scope `N/A` có lý do; vẫn review hiệu quả tài nguyên |

## Red flags

- Gọi một checklist riêng của service là “sáu pillar chuẩn của ngành”.
- Có `RUNNING`, retry hoặc migration nhưng không có terminal/recovery path.
- DB write và event publish có khoảng hở không outbox/compensation.
- Consumer không idempotent hoặc poison message chặn partition vô hạn.
- Chỉ benchmark một service một lần, không có capacity/saturation hypothesis.
- Log lộ secret/path hoặc không có request/event/job identity.
- Migration phá version cũ trong rolling deployment.

## Tóm tắt 30 giây

AWS Well-Architected cung cấp sáu pillar quality để review mọi workload. Google PRR và NIST là nguồn tư duy bổ sung, không chứng minh một checklist toàn cầu duy nhất. Với Backend V2, control state, timeout, outbox, idempotency, fencing, observability và shutdown phải được chọn theo HTTP API, event consumer, worker, projection hay migration. Scan chỉ là một ví dụ; cùng framework áp dụng cho Gateway, Catalog, Media Worker và Query Service.

## Tài liệu tham khảo

- [AWS Well-Architected Framework — The pillars](https://docs.aws.amazon.com/wellarchitected/latest/framework/the-pillars-of-the-framework.html)
- [Microsoft Azure Well-Architected Framework](https://learn.microsoft.com/en-us/azure/well-architected/what-is-well-architected-framework)
- [Google SRE — Production Readiness Review](https://sre.google/sre-book/evolving-sre-engagement-model/)
- [NIST SP 800-160 Rev. 1 — Engineering Trustworthy Secure Systems](https://csrc.nist.gov/pubs/sp/800/160/v1/r1/final)
