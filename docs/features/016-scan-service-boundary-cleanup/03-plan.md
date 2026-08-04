# 016 Scan service boundary cleanup — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`; chỉ refactor cấu trúc source, không đổi API, persistence schema, event, transaction hoặc parse behavior.
- Scope: tách public result type và exception khỏi `ScanService`; giữ `Parsed` là private implementation detail nếu vẫn chỉ phục vụ parse nội bộ.
- Must preserve: Scan OpenAPI response/error hiện có, HTTP status và `ProblemDetail` mapping, behavior scan preview/approval, source dưới 500 dòng/file.
- Read on demand: `apps/scan-service/CONTEXT.md`, `docs/architecture/03-CODING_RULES.md`, `ScanService`, `ScanController`, `ScanExceptionHandler`, `ScanDecisionService` và test Scan liên quan.

## Bước triển khai

1. Liệt kê các public type hiện lồng trong `ScanService` và các điểm dùng ở web/application/test; xác nhận JSON response và error mapping không đổi.
2. Tách exception thành top-level type tên theo resource/ý nghĩa: root scan không hợp lệ, scan run đang chạy, scan run không tồn tại và proposal không tồn tại. Cập nhật `ScanExceptionHandler` và caller tương ứng.
3. Tách các public result record khỏi `ScanService` vào package application phù hợp, dùng tên độc lập với service; giữ result là immutable và không chứa annotation/chi tiết HTTP.
4. Để `ScanController` map application result sang response web nếu cần để giữ ranh giới rõ ràng; không tạo abstraction dùng một lần hoặc tách vụn lớp.
5. Giữ `Parsed` private trong `ScanService` trừ khi refactor tạo một parser owner rõ ràng; không kéo parser strategy ngoài phạm vi task này.
6. Bổ sung/chỉnh test cần thiết cho response/error contract không đổi; format source đã chạm và chạy các kiểm tra được phép.

## Acceptance criteria

- `ScanService` không còn chứa public record hoặc public nested exception.
- Không còn import dạng `ScanService.SomeType` ở source production/test.
- Tên exception phân biệt rõ scan run và proposal, không dùng một `ScanNotFoundException` chung cho hai resource.
- OpenAPI, response body, HTTP status và `ProblemDetail` của Scan không đổi.
- Không thay đổi database, Kafka event, filesystem behavior hoặc service ownership.

## Kiểm tra

- Static: `git diff --check`, không wildcard import, file source đã chạm dưới 500 dòng.
- Khi được phép: `./mvnw test -pl apps/scan-service -am` bằng JDK `corretto-25` và `./mvnw spotless:apply` cho source đã chạm.
- Đọc diff của `ScanController`/`ScanExceptionHandler` để xác nhận contract HTTP không đổi.

## Rollout và rollback

- Không có rollout runtime, migration hoặc dữ liệu cần chuyển đổi.
- Nếu phát hiện response/error contract thay đổi, hoàn tác các type/caller đã tách trong cùng thay đổi source; không cần phục hồi dữ liệu.

## Source-of-truth audit

- Không đổi architecture, service ownership, database schema, REST/Kafka contract hay ADR; đã xóa `TD-001` khỏi active technical-debt backlog khi hoàn tất. Evidence nằm trong Plan này và commit `0750098`.

## Implementation handoff — 2026-08-04

- Hoàn tất tại commit `0750098`: public view records đã chuyển sang `application.dto`; exception Scan đã chuyển sang `application.exception` và caller/web handler dùng top-level type.
- `ScanService` chỉ còn `Parsed` private implementation detail; không còn import `ScanService.SomeType` trong source production/test.
- `ScanController` và `ScanExceptionHandler` đã đổi import nhưng giữ response/error boundary hiện có; không có thay đổi database, Kafka event, filesystem behavior hoặc service ownership.
- Xác minh tĩnh khi đồng bộ status: không có public nested record/exception trong `ScanService`; các DTO/exception top-level hiện có đúng package. Không chạy Maven/Docker trong task cập nhật tracking này.
