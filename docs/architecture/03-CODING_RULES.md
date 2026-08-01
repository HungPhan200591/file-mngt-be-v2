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
- Dùng `record` cho command/query/view/DTO immutable; dùng class cho entity, service, adapter và exception có hành vi.
- Tên thể hiện vai trò: `*Controller`, `*Service`, `*Repository`, `*Entity`, `*Request`, `*Response`, `*Exception`. Một public type chính mỗi file.
- Method ngắn, một mục đích; ưu tiên guard clause. Không tạo utility/generic abstraction khi chỉ dùng một lần.
- Null chỉ ở boundary hoặc khi framework bắt buộc; chuẩn hóa collection thành rỗng ngay tại boundary. Không trả `null` collection.
- Mọi clock/UUID/random không cần abstraction trước; chỉ bọc khi cần deterministic test hoặc có nhiều implementation.

## API, persistence và lỗi

- Validate shape/range ở `adapter.in`; giữ invariant quan trọng ở application và unique/FK/check constraint ở database.
- Transaction đặt ở application use case. Read dùng `readOnly = true`; không mở transaction trong controller.
- Controller chỉ map input/output, gọi một use case và tạo HTTP response. Response/error theo OpenAPI.
- Lỗi nghiệp vụ dự đoán được map `ProblemDetail` tại advice chung của service; không catch `Exception` chung hoặc nuốt lỗi.
- Flyway migration append-only, tên `V<version>__<meaning>.sql`; entity phải validate được với schema. Không dùng `ddl-auto` để tạo schema.

## Test và kiểm tra

- Mỗi behavior mới có ít nhất happy path và failure quan trọng (validation, not-found, conflict hoặc idempotency khi áp dụng).
- Persistence/API integration dùng Testcontainers; không phụ thuộc database local. E2E `.http` chỉ cập nhật khi public contract/flow đổi.
- Trước handoff: test được phép chạy, `git diff --check`, source dưới 500 dòng/file, và audit contract/ownership/doc owner nếu có thay đổi.

## Khi cần exception

- Feature Plan được ưu tiên nếu có trade-off cụ thể. Ghi lý do ngay trong Design/Plan của feature; không sửa rule chung chỉ vì một case.
- Rule chung đổi khi ít nhất hai feature có cùng nhu cầu hoặc nó là invariant dự án.
