# SC-01 — Scan một triệu filesystem entry

Scenario, prerequisite, workload contract và evidence cần thu thập do [roadmap](../../../ADVANCED_MICROSERVICES_STUDY_ROADMAP.md) sở hữu. Trạng thái study: đang nghiên cứu; chưa có benchmark chứng minh V2 đạt quy mô này.

## Study pack

### Entry point cho task tiếp theo

Để tiếp tục tối ưu SC-01, Agent chỉ cần đọc theo thứ tự; các artifact khác là on-demand:

1. [08-approve-1m-context.md](./08-approve-1m-context.md) — context ngắn mặc định.
2. Đúng section `BT-09` trong [04-break-task.md](./04-break-task.md) — chỉ khi cần break task.
3. [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md) — SLO contract chính thức và hardware envelope; chỉ đọc khi cần.
4. [06-performance-and-cloud-scaling.md](./06-performance-and-cloud-scaling.md) hoặc review/Plan của đúng hypothesis — không đọc toàn bộ lịch sử.

Review `approve 5.000` chỉ là calibration evidence; mục tiêu workload của SC-01 là approve **1.000.000 records**. `manual/` là study owner, không thay thế `docs/STATUS.md`, architecture, contract hoặc feature Plan.

| Artifact | Vị trí | Trạng thái |
| --- | --- | --- |
| Deep-dive | [01-deep-dive.md](./01-deep-dive.md) | Đã tạo |
| Chi tiết Luồng & Điểm chạm | [02-architecture-touchpoints-and-flows.md](./02-architecture-touchpoints-and-flows.md) | Đã tạo |
| Giải pháp Lọc trùng Xuyên Service | [03-cross-service-deduplication.md](./03-cross-service-deduplication.md) | Đã tạo |
| Context approve 1M | [08-approve-1m-context.md](./08-approve-1m-context.md) | Context mặc định |
| Break task triển khai | [04-break-task.md](./04-break-task.md) | Owner hiện hành |
| Phương án UI/UX Solution Behavior | [05-ui-ux-solution-behavior.md](./05-ui-ux-solution-behavior.md) | Đã tạo |
| Khảo sát Scale & Cloud | [06-performance-and-cloud-scaling.md](./06-performance-and-cloud-scaling.md) | Hypothesis/evidence |
| Workload, SLI/SLO & Benchmark | [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md) | SLO chính thức, không load mặc định |
| Fixture và microbenchmark commands | [09-fixture-and-microbenchmarks.md](./09-fixture-and-microbenchmarks.md) | Chỉ đọc khi chạy benchmark |
| Java 25 Fixture Tool | [fixture-tools](../../../../../tests/fixtures/tools/pom.xml) (package `com.filemngt.tools.sc01_scan_one_million`) | Đã tạo |
| Summary | [summary/01-issues-and-solutions.md](./summary/01-issues-and-solutions.md) | Đã tạo; distill từ issue/solution/evidence |
| Question chain | [question-bank/01-question-chain.md](./question-bank/01-question-chain.md) | Đã tạo, bổ sung dần theo BT/deep-dive |

Xem [UC-01](../../core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) cho flow nhỏ làm prerequisite.
Không thay architecture, contract, owner context hoặc `docs/STATUS.md`. Đọc
[09-fixture-and-microbenchmarks.md](./09-fixture-and-microbenchmarks.md) chỉ khi task yêu cầu chạy
fixture hoặc benchmark; không nạp file này cho task design/implementation thông thường.
