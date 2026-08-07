---
name: production-readiness-review
description: Perform a full Production Readiness review for Backend V2 using the six AWS Well-Architected pillars plus project-specific workflow reliability controls. Use for release/cutover, a new service, async worker or scheduler, messaging pipeline, major migration, or explicit production-readiness assessment; not for ordinary diffs.
---

# Production Readiness Review

Review chuyên sâu trước release/cutover. Khung tham chiếu là sáu pillar của AWS Well-Architected: Operational Excellence, Security, Reliability, Performance Efficiency, Cost Optimization, Sustainability. Đây là framework công bố bởi AWS, không phải tiêu chuẩn/chứng nhận toàn ngành; Azure dùng năm pillar tương tự nhưng không có Sustainability. Sáu control workflow của Backend V2 là overlay chi tiết cho Reliability và Operational Excellence, không phải sáu pillar chính thức.

## Phạm vi và nguyên tắc

- Chỉ dùng khi release/cutover, service mới, background worker/scheduler, messaging pipeline, major migration hoặc người dùng yêu cầu full review.
- Đọc source of truth, design/plan, diff và dependency runtime trực tiếp. Mọi finding có evidence; rủi ro chỉ waived khi người dùng chấp nhận rõ.
- Không tự sửa, chạy test/build, migration, service, Docker, commit hoặc push khi chỉ được yêu cầu review.
- Kết luận từng pillar `PASS`, `PARTIAL`, `MISSING`, nêu blocker, residual risk và owner/follow-up. Áp dụng control tương xứng risk; không biến mọi control thành checkbox cho mọi service.

## Sáu pillar đánh giá

### 1. Operational Excellence

- Vận hành, quan sát, runbook, alert, change management, rollback và continuous improvement.
- Kiểm tra structured log, durable job identity như `runId`, metrics/health/trace đúng mức. Request MDC không mặc định đi qua async boundary.
- Kiểm tra shutdown policy: stop intake, drain giao dịch ngắn, cancel/mark long-running work theo policy và restart recovery.

### 2. Security

- Kiểm tra authn/authz, least privilege, secrets, mã hóa phù hợp, audit, dependency/supply-chain và dữ liệu nhạy cảm trong log/API.
- Với mọi service, không để identifier nội bộ, absolute path, secret hoặc dữ liệu nghiệp vụ nhạy cảm lộ qua response/log không được bảo vệ.

### 3. Reliability

- Trace state machine: mọi non-terminal state có đường `COMPLETED`, `FAILED`, `CANCELLED` khi success, exception, timeout, cancel, dependency crash và restart.
- Từng blocking I/O có timeout/deadline enforce, recovery khi mất progress và cleanup idempotent.
- Nếu dùng lease, kiểm tra `operation timeout < no-progress/lease deadline < total deadline`, fencing token/conditional update và race giữa worker, expiry handler, cleanup, restart. Lease không tự fail run nếu không có handler.
- Kiểm tra transaction boundary, DB ownership, constraint/index, compensation, idempotency; DB write cần phát event dùng transactional outbox trong cùng local transaction.

### 4. Performance Efficiency

- Có capacity model/SLO cho latency, throughput, concurrency, data volume, saturation và back-pressure.
- Kiểm tra bounded batch/memory, query/index/I/O path, load evidence và degradation khi quá capacity.
- Với workload lớn, nêu rõ data volume/concurrency target, giới hạn memory/batch, DB/IO bottleneck và back-pressure/progress cadence.

### 5. Cost Optimization

- Nêu resource budget và driver chi phí: compute, storage, DB I/O, network, Kafka, retention.
- Kiểm tra retry/polling/batch/retention không tạo chi phí vô ích; quyết định tối ưu phải so với SLO và reliability.

### 6. Sustainability

- Với workload dài hoặc quy mô lớn, kiểm tra tránh CPU/IO/DB work vô ích, right-size resource và lifecycle/retention dữ liệu.
- Với local/study workload, có thể ghi `N/A` kèm lý do và resource-efficiency evidence thay vì tạo một checklist giả.

## Workflow controls chuyên sâu cho job dài

Đây là cách tách nhỏ pillar để review scan/async worker sâu hơn, không thay thế sáu pillar ở trên:

1. State machine & terminal guarantee.
2. Liveness & fencing safety.
3. Data consistency & transaction boundary.
4. Dependency resilience & fault tolerance.
5. Observability & operability.
6. Deployment, restart recovery & graceful shutdown.

## Quy trình và verdict

1. Chốt scope release, SLO/time budget, owner, dependency map, data classification và capacity/cost assumptions.
2. Trace happy path, failure path, overload path, shutdown/restart path theo sáu pillar.
3. Tạo finding `Critical`, `High`, `Medium`, `Low`; blocker nêu condition, impact, smallest useful remediation.
4. `READY`: không Critical/High và evidence đủ. `CONDITIONAL`: còn Medium/Low với waiver/plan rõ. `NOT READY`: không chứng minh terminal/liveness/consistency/security hoặc SLO/capacity quan trọng.

```text
Scope / release decision: <...>
Verdict: READY | CONDITIONAL | NOT READY
Well-Architected pillars 1..6: [PASS|PARTIAL|MISSING] — Evidence: <file/symbol/doc>
Workflow controls: <state; liveness/fencing; consistency; resilience; observability; shutdown or N/A>
SLO / capacity / cost assumptions: <...>
Blockers: [severity] <condition>; <impact>; <remediation>
Runbook & rollback: <...>
Accepted risks / waivers: <explicit user approval or None>
```

## Ranh giới với review thường

`architecture-quality-review` là mặc định cho diff/feature và luôn kiểm tra correctness, boundary, liveness thiết yếu. Skill này thêm release evidence, Security, Performance/Capacity, Cost/Sustainability, runbook và rollout; không dùng mặc định để tránh review nhỏ tốn kém không cần thiết.
