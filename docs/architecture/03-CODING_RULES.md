# Backend V2 — Coding rules

Mục tiêu: code ít lớp nhưng rõ owner, nhất quán và dễ review. Áp dụng cho mọi Java source mới/sửa; feature có thể bổ sung rule cục bộ khi thật sự cần.

## Package và dependency

| Package | Trách nhiệm | Không được làm |
| --- | --- | --- |
| `domain` | Type nghiệp vụ, invariant, enum/value object | Phụ thuộc Spring, HTTP, JPA, Kafka hoặc database khác |
| `application` | Use case, transaction, orchestration, policy | Nhận/trả HTTP DTO, chứa controller hoặc query web |
| `adapter.in` | HTTP/event input, validation biên, map request/response | Chứa nghiệp vụ hoặc trả JPA entity |
| `adapter.out` | JPA, filesystem, Kafka, HTTP client | Quyết định nghiệp vụ của use case |
| `config` | Wiring/configuration kỹ thuật | Chứa use case/domain logic |

- Luồng phụ thuộc mặc định: `adapter.in → application → domain`; adapter persistence/integration chỉ nằm ở `adapter.out`.
- Với vertical slice nhỏ, application có thể dùng repository adapter trực tiếp nếu chưa có nhiều implementation. Tạo port khi nó che giấu dependency hữu ích, có từ hai implementation, hoặc là boundary ngoài service; không tạo interface chỉ để đủ pattern.
- Không service nào import repository/entity/database của service khác. Không trả entity qua API.

## Cách viết Java

- Java 25, constructor injection; không field injection, service locator, static mutable state hoặc `Optional` field/parameter.
- Format Java là **Palantir Java Format**, chạy bằng `./mvnw spotless:apply`; Maven tự chạy `spotless:check` ở phase `validate`. Không sửa format thủ công theo sở thích cá nhân.
- Source dùng UTF-8, LF, 4 spaces, không tab, không trailing whitespace; dòng dài để formatter tự wrap (mục tiêu 80 ký tự). `.editorconfig` là cấu hình IDE nền.
- Import không wildcard; để formatter tối ưu thứ tự import. Annotation, field, method, control flow và brace theo output formatter; không nén nhiều statement/khai báo vào một dòng.
- Trước handoff, Agent chạy formatter cho file Java đã chạm; với IntelliJ, bật EditorConfig và dùng `Ctrl+Alt+L` trước khi lưu nếu cần đọc diff ngay.
- Dùng `record` cho command/query/view/DTO immutable; dùng class cho entity, service, adapter và exception có hành vi.
- Tên thể hiện vai trò: `*Controller`, `*Service`, `*Repository`, `*Entity`, `*Request`, `*Response`, `*Exception`. Một public type chính mỗi file.
- Method ngắn, một mục đích; ưu tiên guard clause. Không tạo utility/generic abstraction khi chỉ dùng một lần.
- Null chỉ ở boundary hoặc khi framework bắt buộc; chuẩn hóa collection thành rỗng ngay tại boundary. Không trả `null` collection.
- Mọi clock/UUID/random không cần abstraction trước; chỉ bọc khi cần deterministic test hoặc có nhiều implementation.

## Giữ code dễ thay đổi

- Trước khi code, khảo sát type owner, package lân cận và dependency trực tiếp để đặt trách nhiệm đúng layer/package. Sau khi code, tự audit và refactor phần vừa chạm trước khi handoff; không chờ một task refactor riêng cho code mới.
- Method tối đa 30 dòng logic, 4 parameter (không tính constructor) và 2 mức nesting; dùng guard clause. Tuyệt đối không quá 50 dòng. Nếu buộc giữ quá 30 dòng, comment ngắn ngay trước method nêu lý do không thể tách an toàn.
- Class tối đa 250 dòng và 7 constructor dependency. Vượt ngưỡng là tín hiệu tách theo trách nhiệm/capability; tuyệt đối không quá 500 dòng. Nếu tạm vượt 250 dòng, Javadoc class phải nêu cohesion và lý do giữ nguyên.
- Tách method khi nó trộn điều phối use case với validate, map, policy, tạo object hoặc I/O. Tách class/package khi có nhiều lý do thay đổi; không tạo lớp chỉ forward lời gọi hoặc abstraction một lần dùng.
- Với mỗi entry point mới hoặc sửa, phân hoạch đầu vào và trạng thái làm đổi control flow; trace tối thiểu happy path,
  no-op/boundary, failure, retry/re-entry và concurrency khi áp dụng. Side effect DB, network, event, metric và log
  phải tương ứng với công việc thực sự hoàn thành.
- Scheduled/batch worker phải kiểm tra cardinality `0`, `1` và đầy batch. Batch rỗng là no-op/boundary path và
  mặc định return trước dispatch, ghi DB, tăng success metric hoặc log INFO, trừ khi có housekeeping được mô tả rõ.
- Chuỗi `if/else` quá 3 nhánh phải đổi thành guard clause, exhaustive `switch`, lookup table hoặc Strategy tùy biến thể nghiệp vụ. Magic string/number có nghĩa, status, regex, threshold và issue code lặp lại phải thành constant hoặc type phù hợp.
- Package trực tiếp tối đa 8 production type; root package của một layer tối đa 5. Leaf package thường có 2–8 type cùng thay đổi vì một lý do. Tách theo capability/domain trước; cấm `util`, `common`, `misc`, `helper` làm nơi gom code không owner.
- Ưu tiên enum, record, sealed type hoặc value object cho tập giá trị đóng; không dùng raw `String`, `Object`, `Map<String, Object>` hay raw generic trong core logic khi có type nghiệp vụ rõ hơn.

## Comment phục vụ quyết định

- Comment/Javadoc viết tiếng Việt có dấu, giải thích trách nhiệm, boundary, invariant hoặc lý do kỹ thuật không suy ra từ tên code.
- Public use case có side effect, transaction, batch rule hoặc ngữ nghĩa không hiển nhiên phải mô tả mục đích nghiệp vụ và điều kiện quan trọng.
- Không comment lại từng câu lệnh, getter/setter, DTO/record/enum/repository theo convention. Tên rõ ràng là tài liệu đầu tiên.

## Tài liệu phiên bản và API

- Stack hiện hành được pin ở root `pom.xml` và Compose. Với Spring Boot 4/Spring Framework 7/Spring Kafka 4,
  Java 25, Flyway, Testcontainers, Kafka client/broker hoặc tool/library tương tự, không suy luận API từ kiến thức
  cũ hay snippet trên mạng.
- Trước khi viết/sửa code có chạm API signature, annotation, configuration property, lifecycle, compatibility,
  retry/error handling, migration hoặc setup của library/tool, dùng `$find-docs`: resolve library trước, rồi đọc
  đúng tài liệu chính thức/current cho một vấn đề cụ thể.
- Không cần tra docs cho pure domain logic, đổi tên, format hoặc refactor không đổi behavior của framework. Khi tài
  liệu và code mẫu cũ mâu thuẫn, version đang pin trong dự án là chuẩn; cập nhật implementation theo docs của version đó.
- Ưu tiên capability mới khi nó làm code đúng hơn, đơn giản hơn hoặc đo được lợi ích; không thêm pattern/công nghệ chỉ
  vì mới. Ghi vào Feature Design/Plan nếu adoption đổi contract, vận hành hoặc compatibility dài hạn.

## API, persistence và lỗi

- Validate shape/range ở `adapter.in`; giữ invariant quan trọng ở application và unique/FK/check constraint ở database.
- Transaction đặt ở application use case. Read dùng `readOnly = true`; không mở transaction trong controller.
- Controller chỉ map input/output, gọi một use case và tạo HTTP response. Response/error theo OpenAPI.
- Lỗi nghiệp vụ dự đoán được map `ProblemDetail` tại advice chung của service; không catch `Exception` chung hoặc nuốt lỗi.
- Flyway migration append-only, tên `V<version>__<meaning>.sql`; entity phải validate được với schema. Không dùng `ddl-auto` để tạo schema.

## Local configuration

- Mọi service mặc định profile `local`. Dùng `src/main/resources/application-local.yml` cho path/config local; file này bị gitignore.
- Commit `application-local.example.yml` khi service cần local setting mới. Không dùng `.env`/plugin IDE làm cơ chế runtime chuẩn.
- Environment variable vẫn được phép override cho Docker/CI; không commit secret hoặc machine-specific path.

## Dependency version

- Version third-party dùng từ hai module trở lên phải pin một lần tại root `pom.xml` (property hoặc imported BOM); service POM chỉ khai báo dependency không lặp version. Version chỉ dùng riêng một service có thể ở POM owner khi có lý do rõ ràng.

## Test và kiểm tra

- Maven của Agent luôn dùng trực tiếp IntelliJ Project SDK `corretto-25` (JDK 25); không dùng Java hệ thống hoặc JDK terminal mặc định.

- Mỗi behavior mới có ít nhất happy path và failure quan trọng (validation, not-found, conflict hoặc idempotency khi áp dụng).
- Persistence/API integration dùng Testcontainers; không phụ thuộc database local. E2E `.http` chỉ cập nhật khi public contract/flow đổi.
- Trước handoff: test được phép chạy, `git diff --check`, source dưới 500 dòng/file, và audit contract/ownership/doc owner nếu có thay đổi.

## Khi cần exception

- Feature Plan được ưu tiên nếu có trade-off cụ thể. Ghi lý do ngay trong Design/Plan của feature; không sửa rule chung chỉ vì một case.
- Rule chung đổi khi ít nhất hai feature có cùng nhu cầu hoặc nó là invariant dự án.
