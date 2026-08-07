# SC-01 — Scan một triệu filesystem entry

Scenario, prerequisite, workload contract và evidence cần thu thập do [roadmap](../../../ADVANCED_MICROSERVICES_STUDY_ROADMAP.md) sở hữu. Trạng thái study: đang nghiên cứu; chưa có benchmark chứng minh V2 đạt quy mô này.

## Study pack

| Artifact | Vị trí | Trạng thái |
| --- | --- | --- |
| Deep-dive | [01-deep-dive.md](./01-deep-dive.md) | Đã tạo |
| Chi tiết Luồng & Điểm chạm | [02-architecture-touchpoints-and-flows.md](./02-architecture-touchpoints-and-flows.md) | Đã tạo |
| Giải pháp Lọc trùng Xuyên Service | [03-cross-service-deduplication.md](./03-cross-service-deduplication.md) | Đã tạo |
| Break task triển khai | [04-break-task.md](./04-break-task.md) | Đã tạo |
| Java 25 Fixture Tool | [fixture-tools](../../../../tests/fixtures/tools/pom.xml) (package `com.filemngt.tools.sc01_scan_one_million`) | Đã tạo |
| Summary | `summary/` | Chưa yêu cầu |
| Question chain | [question-bank/01-question-chain.md](./question-bank/01-question-chain.md) | Đã tạo, bổ sung dần theo BT/deep-dive |
| Evidence benchmark/failure drill | Link từ đây khi có | Chưa tạo |

Summary chỉ cô đọng từ deep-dive; question bank chỉ sinh từ nội dung đã kiểm chứng. Không tạo folder rỗng hoặc placeholder cho hai artifact này.

Xem [UC-01](../../core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) cho flow nhỏ làm prerequisite. Không thay architecture, contract, owner context hoặc `docs/STATUS.md`.

Chạy nhanh qua NPM scripts từ root repository:

```bash
# Sinh 1 triệu file fixture rỗng cho SC-01
npm run fixture:sc01:gen

# Dọn dẹp / xóa sạch 1 triệu file fixture SC-01
npm run fixture:sc01:clean

# Xem trợ giúp lệnh fixture
npm run help:fixture
```

Hoặc chạy trực tiếp CLI / Maven compile:

```text
mvn -f tests/fixtures/tools/pom.xml compile
java -cp tests/fixtures/tools/target/classes -Dconcurrency=32 com.filemngt.tools.sc01_scan_one_million.GenerateOneMillionJokeVideoFixtures
java -cp tests/fixtures/tools/target/classes com.filemngt.tools.sc01_scan_one_million.CleanOneMillionJokeVideoFixtures
```
