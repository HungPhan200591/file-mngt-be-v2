# SC-01 — Scan một triệu filesystem entry

Scenario, prerequisite, workload contract và evidence cần thu thập do [roadmap](../../../ADVANCED_MICROSERVICES_STUDY_ROADMAP.md) sở hữu. Trạng thái study: đang nghiên cứu; chưa có benchmark chứng minh V2 đạt quy mô này.

## Study pack

| Artifact | Vị trí | Trạng thái |
| --- | --- | --- |
| Deep-dive | [01-deep-dive.md](./01-deep-dive.md) | Đã tạo |
| Helper Class (Java 25 - Generator) | [GenerateOneMillionJokeVideoFixtures.java](../../../../apps/scan-service/src/test/java/com/filemngt/v2/scan/helper/GenerateOneMillionJokeVideoFixtures.java) | Đã tạo |
| Cleaner Class (Java 25 - Cleanup) | [CleanOneMillionJokeVideoFixtures.java](../../../../apps/scan-service/src/test/java/com/filemngt/v2/scan/helper/CleanOneMillionJokeVideoFixtures.java) | Đã tạo |
| Summary | `summary/` | Chưa yêu cầu |
| Question bank | `question-bank/` | Chưa yêu cầu |
| Evidence benchmark/failure drill | Link từ đây khi có | Chưa tạo |

Summary chỉ cô đọng từ deep-dive; question bank chỉ sinh từ nội dung đã kiểm chứng. Không tạo folder rỗng hoặc placeholder cho hai artifact này.

Xem [UC-01](../../core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) cho flow nhỏ làm prerequisite. Không thay architecture, contract, owner context hoặc `docs/STATUS.md`.
