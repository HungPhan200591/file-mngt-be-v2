---
name: refactor-spring-service
description: Refactor và audit clean code Java 25/Spring Boot 4 theo kiến trúc Backend V2, tập trung tách hàm, tách file, SOLID, type safety, performance và pattern phù hợp mà không đổi hành vi. Dùng khi cleanup code bẩn, xử lý class/method dài, tách semantic/business khỏi orchestrator, sửa N+1 hoặc refactor một service/module Java hiện có; không dùng cho feature mới hay thay đổi contract/nghiệp vụ.
---

# Refactor Spring Service

## Nạp context tối thiểu

1. Đọc `docs/architecture/01-SUMMARY.md`, `apps/<service>/CONTEXT.md` và
   `docs/architecture/03-CODING_RULES.md`.
2. Kiểm tra `git status --short`; giữ nguyên mọi thay đổi sẵn có của người dùng.
3. Nếu người dùng chỉ định commit/range, đọc diff của đúng range đó trước.
4. Chỉ đọc source, test và dependency trực tiếp của vùng cần refactor.

## Giữ đúng phạm vi

- Giữ nguyên business behavior, REST/Kafka contract, schema và transaction boundary.
- Không trộn bug fix, feature, migration hay nâng dependency vào refactor.
- Nếu phát hiện hành vi sai, ghi nhận riêng; chỉ sửa khi người dùng yêu cầu.
- Nếu buộc phải đổi nghiệp vụ hoặc contract, dừng phần đó và route sang skill phù hợp.

## Ngưỡng clean code

- Method body tối đa 30 dòng logic, nesting tối đa 2 tầng và tối đa 4 parameter không tính constructor.
- Method trên 30 dòng phải có comment ngay phía trên giải thích vì sao giữ nguyên khối; không method nào quá 50 dòng.
- Source class tối đa 250 dòng. Class trên 250 dòng phải có comment trên class giải thích cohesion; tuyệt đối không quá 500 dòng.
- Constructor tối đa 7 dependency; nhiều hơn là tín hiệu class có quá nhiều trách nhiệm.
- Chuỗi `if/else` trên 3 nhánh phải đổi sang guard clause, exhaustive `switch`, lookup table hoặc Strategy.
- Một method làm một việc ở cùng mức trừu tượng; tên phải mô tả ý định, không mô tả cơ chế.

## Constant và type safety

- Đưa magic number/string có ý nghĩa, threshold, regex, event type, status, issue code và giá trị lặp lại vào constant.
- Không tạo constant cho `0`, `1`, chuỗi một lần dùng đã tự giải thích hoặc dữ liệu test cục bộ.
- Dùng enum/sealed type/value record cho tập giá trị đóng; tránh raw `String`, `Object` và `Map<String,Object>` trong core logic.
- Dùng generic khi cùng một thuật toán/type shape phục vụ ít nhất hai type; đặt bound rõ và không dùng raw type.
- Không tạo generic wrapper, base class hoặc utility chỉ có một use case.

## SOLID và ranh giới

- `domain` chỉ giữ semantic, invariant, policy và type thuần; không phụ thuộc Spring, JPA, HTTP, JSON hay `adapter`.
- `application` chỉ điều phối use case/transaction; không parse filename, regex, JSON payload hoặc chứa query web.
- `adapter.in` validate/map request-response; `adapter.out` sở hữu filesystem, HTTP, Kafka và persistence.
- Tách file khi class có nhiều lý do thay đổi; không tách thành class chỉ forward call hoặc utility dùng một lần.
- Chỉ tạo port/interface ở boundary ngoài service, khi có nhiều implementation hoặc khi nó che giấu dependency có giá trị.

## Cấu trúc package

- Mỗi package trực tiếp tối đa 8 production type, không tính `package-info.java`; package gốc của một layer tối đa 5 type.
- Khi vượt ngưỡng, tách theo capability/domain responsibility trước; chỉ adapter mới ưu tiên nhóm theo kỹ thuật như HTTP, Kafka hoặc persistence aggregate.
- Một leaf package nên có 2-8 type cùng thay đổi vì một lý do. Chấp nhận một type khi đó là entrypoint, use-case owner hoặc external boundary độc lập.
- Không tạo package `util`, `common`, `misc`, `helper` để gom code thừa; đặt helper package-private cạnh owner sử dụng nó.
- Subpackage domain chỉ phụ thuộc theo hướng model ổn định; cấm dependency cycle và cấm dùng package split để che coupling sai tầng.
- Test mirror package của production type; sau khi move phải audit package declaration, import và số type trực tiếp từng package.

## Comment phục vụ đọc nghiệp vụ

- Comment/Javadoc viết tiếng Việt có dấu, nêu trách nhiệm của class và boundary nó sở hữu.
- Public method/use case có side effect, transaction, batch rule hoặc semantic không hiển nhiên phải mô tả mục đích nghiệp vụ và điều kiện quan trọng.
- Private method chỉ comment khi giữ một invariant, thứ tự xử lý hoặc lý do kỹ thuật không thể suy ra từ tên; không comment lại từng câu lệnh.
- Không comment getter, setter, record/DTO/enum/repository theo convention rõ ràng; tên tốt là tài liệu đầu tiên.

## Pattern và functional style

- Dùng Strategy + Registry cho các biến thể thay đổi độc lập như parser JOKE/USE hoặc policy theo profile.
- Dùng Factory khi việc dựng object có nhiều invariant, metadata hoặc dependency như event/outbox.
- Dùng Template Method chỉ khi skeleton ổn định, hook thay đổi rõ và quan hệ kế thừa đúng nghĩa; ưu tiên composition.
- Dùng Specification cho điều kiện lọc có thể tổ hợp; Adapter cho boundary kỹ thuật; không áp pattern theo quota.
- Ưu tiên `Function`, `Predicate`, `Consumer`, `Supplier`; tạo functional interface riêng khi tên domain tăng ý nghĩa.
- Lambda phải ngắn, không giấu transaction, repository call hoặc side effect khó thấy.
- Dùng `record`, sealed type, pattern matching và exhaustive `switch` Java 25 khi làm model rõ hơn.

## Persistence và performance

- Cấm gọi repository/HTTP trong loop hoặc stream; bulk fetch/write, join fetch, projection hoặc batch theo tập ID.
- Với collection mapping, audit N+1 trước khi bàn giao; nếu buộc query theo item phải chứng minh tập dữ liệu bị chặn và comment lý do.
- Chỉ load field/row cần dùng, luôn phân trang list lớn và không materialize toàn bảng để lọc trong memory.
- Transaction nằm ở application use case, ngắn và không bao remote I/O; write liên quan phải giữ atomicity.

## Thực hiện

1. Chỉ ra responsibility, dependency sai tầng, code smell và query pattern của vùng cần sửa.
2. Dùng test hiện có làm characterization; bổ sung test khi semantic hoặc failure path chưa được khóa.
3. Refactor theo lát kiểm chứng được: rename, extract method/class, move method, typed model, bulk query hoặc đảo dependency.
4. Sau mỗi lát, compile/test tập trung; chạy formatter dự án cho Java đã chạm.

## Bàn giao

- Chạy test module, `git diff --check`; audit line cap, nesting, constant, raw type, N+1 và repository-in-loop.
- Review diff để bảo đảm không đổi contract, persistence ownership hoặc behavior ngoài ý muốn.
- Chỉ cập nhật architecture/context/contract khi owner hoặc boundary thật sự thay đổi.
- Tóm tắt phần đã tách/gọn, test đã chạy, hành vi được giữ và tối đa ba điểm còn nên làm tiếp.
