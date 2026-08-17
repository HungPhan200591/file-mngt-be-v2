# SC-01 — Scan một triệu filesystem entry

Scenario, prerequisite, workload contract và evidence cần thu thập do [roadmap](../../../ADVANCED_MICROSERVICES_STUDY_ROADMAP.md) sở hữu. Trạng thái study: đang nghiên cứu; chưa có benchmark chứng minh V2 đạt quy mô này.

## Study pack

### Entry point cho task tiếp theo

Đây là router hai tầng để tránh nạp cả study pack vào context:

1. Agent mặc định đọc [08-approve-1m-context.md](./08-approve-1m-context.md).
2. Khi làm một lát BT-09, đọc đúng một capsule `references/ref-bt09x-*.md` được router chỉ định.
3. Chỉ đọc [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md) khi task chạm target, hardware envelope hoặc qualification.
4. Chỉ đọc `04-break-task.md`, contract, feature Plan hoặc review của đúng lát khi router/capsule yêu cầu.

`ref-*.md` là tầng Agent: ngắn, có invariant và đường dẫn owner, không chứa giải thích dài.
`explain-*.md`, các file `01–06`, `summary/`, `question-bank/` và review là tầng con người: dùng để học,
giải thích hoặc truy nguyên evidence; Agent không tự nạp khi triển khai task. File `07` là SLO contract
chính thức, chỉ nạp on-demand khi task chạm target/qualification. Chỉ tạo/cập nhật `explain-*` khi người dùng
yêu cầu deep-dive tương ứng.

Review `approve 5.000` chỉ là calibration evidence; mục tiêu workload của SC-01 là approve **1.000.000 records**. `manual/` là study owner, không thay thế `docs/STATUS.md`, architecture, contract hoặc feature Plan.

| Artifact | Vị trí | Trạng thái |
| --- | --- | --- |
| Deep-dive / giải thích | [01-deep-dive.md](./01-deep-dive.md) | Con người, on-demand |
| Chi tiết Luồng & Điểm chạm | [02-architecture-touchpoints-and-flows.md](./02-architecture-touchpoints-and-flows.md) | Con người, on-demand |
| Giải pháp Lọc trùng Xuyên Service | [03-cross-service-deduplication.md](./03-cross-service-deduplication.md) | Con người, on-demand |
| Context approve 1M | [08-approve-1m-context.md](./08-approve-1m-context.md) | Context mặc định |
| Kiến trúc end-to-end (canonical) | [04-SC-01-1M-scan-approve-end-to-end-architecture.md](../../../../../docs/architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md) | Architecture baseline, proposal |
| Break task triển khai | [04-break-task.md](./04-break-task.md) | Router/owner, đọc đúng section |
| Phương án UI/UX Solution Behavior | [05-ui-ux-solution-behavior.md](./05-ui-ux-solution-behavior.md) | Con người, on-demand |
| Khảo sát Scale & Cloud | [06-performance-and-cloud-scaling.md](./06-performance-and-cloud-scaling.md) | Con người, on-demand |
| Workload, SLI/SLO & Benchmark | [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md) | SLO chính thức, không load mặc định |
| Fixture và microbenchmark commands | [09-fixture-and-microbenchmarks.md](./09-fixture-and-microbenchmarks.md) | Chỉ đọc khi chạy benchmark |
| Java 25 Fixture Tool | [fixture-tools](../../../../../tests/fixtures/tools/pom.xml) (package `com.filemngt.tools.sc01_scan_one_million`) | Đã tạo |
| Summary | [summary/01-issues-and-solutions.md](./summary/01-issues-and-solutions.md) | Con người, on-demand |
| Question chain | [question-bank/01-question-chain.md](./question-bank/01-question-chain.md) | Con người, on-demand |

Xem [UC-01](../../core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) cho flow nhỏ làm prerequisite.
Không thay architecture, contract, owner context hoặc `docs/STATUS.md`. Đọc
[09-fixture-and-microbenchmarks.md](./09-fixture-and-microbenchmarks.md) chỉ khi task yêu cầu chạy
fixture hoặc benchmark; không nạp file này cho task design/implementation thông thường.
