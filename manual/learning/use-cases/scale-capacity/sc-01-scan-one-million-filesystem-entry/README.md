# SC-01 — Scan một triệu filesystem entry

Scenario, prerequisite, workload contract và evidence cần thu thập do [roadmap](../../../ADVANCED_MICROSERVICES_STUDY_ROADMAP.md) sở hữu. Trạng thái study: đang nghiên cứu; chưa có benchmark chứng minh V2 đạt quy mô này.

## Study pack

| Artifact | Vị trí | Trạng thái |
| --- | --- | --- |
| Deep-dive | [01-deep-dive.md](./01-deep-dive.md) | Đã tạo |
| Java 25 Fixture Tool | [fixture-tools](../../../../tests/fixtures/tools/pom.xml) (package `com.filemngt.tools.sc01_scan_one_million`) | Đã tạo |
| Summary | `summary/` | Chưa yêu cầu |
| Question bank | `question-bank/` | Chưa yêu cầu |
| Evidence benchmark/failure drill | Link từ đây khi có | Chưa tạo |

Summary chỉ cô đọng từ deep-dive; question bank chỉ sinh từ nội dung đã kiểm chứng. Không tạo folder rỗng hoặc placeholder cho hai artifact này.

Xem [UC-01](../../core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) cho flow nhỏ làm prerequisite. Không thay architecture, contract, owner context hoặc `docs/STATUS.md`.

Build/chạy tool từ project root; output mặc định nằm ngoài repository:

```text
mvn -f tests/fixtures/tools/pom.xml compile
java -cp tests/fixtures/tools/target/classes -Dconcurrency=32 com.filemngt.tools.sc01_scan_one_million.GenerateOneMillionJokeVideoFixtures
java -cp tests/fixtures/tools/target/classes com.filemngt.tools.sc01_scan_one_million.CleanOneMillionJokeVideoFixtures
```
