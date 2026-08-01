# Bản đồ dự án Backend V2

Tài liệu này dành cho người vận hành và phát triển dự án. Nó giải thích nơi lưu từng loại nội dung và thời điểm cần tạo hoặc cập nhật file. Kiến trúc, API và quyết định kỹ thuật vẫn lấy `docs/` làm nguồn chuẩn; tài liệu này chỉ dẫn đường đến đúng nơi.

## Nhìn toàn cảnh

```text
file_mngt_microservice/
├─ apps/                 Năm Spring Boot service độc lập
├─ platform/             Mã dùng chung có kiểm soát
├─ infra/                Docker Compose và hạ tầng local
├─ tests/                Kịch bản E2E dùng được bằng IntelliJ và CLI
├─ docs/                 Nguồn chuẩn về kiến trúc, feature và contract
├─ manual/               Hướng dẫn cho người dùng; không nạp mặc định cho Agent
├─ .agents/              Workflow ngắn để Agent đọc đúng context
├─ pom.xml               Maven parent: module, Java version, format check
├─ .editorconfig         Quy tắc format nền cho IDE
└─ AGENTS.md             Router: Agent cần đọc gì trước mỗi loại task
```

## Code nằm ở đâu

| Vị trí | Lưu gì | Khi nào sửa/tạo |
| --- | --- | --- |
| `apps/gateway-service/` | API gateway, route, correlation ID, timeout | Khi frontend cần đi qua route V2 mới hoặc cần cross-cutting HTTP |
| `apps/catalog-service/` | Nguồn dữ liệu chuẩn: subject, asset, actress, studio, tag | Khi thay đổi metadata canonical hoặc API write |
| `apps/scan-service/` | Scan filesystem, parser path/filename, proposal review | Khi thêm root/profile scan hoặc luồng review/approval |
| `apps/media-worker/` | Job nền: metadata kỹ thuật, thumbnail, GIF, hash | Khi thêm một loại xử lý media bất đồng bộ |
| `apps/query-service/` | Read model cho Gallery Web/Library, filter, search, cache | Khi thêm màn hình đọc, filter, search, projection |
| `platform/event-contracts/` | Type envelope tối thiểu dùng chung cho event | Chỉ sửa khi shape hạ tầng event dùng chung thật sự đổi |
| `platform/test-support/` | Test utility dùng lại giữa nhiều service | Chỉ tạo sau khi ít nhất hai service cần cùng helper |

Mỗi service có cấu trúc cùng ý nghĩa:

```text
apps/<service>/
├─ src/main/java/.../
│  ├─ domain/        Type và invariant nghiệp vụ, không phụ thuộc Spring/JPA
│  ├─ application/   Use case, transaction, orchestration
│  ├─ adapter/in/    HTTP hoặc Kafka input, validation và map DTO
│  ├─ adapter/out/   JPA, filesystem, Kafka, HTTP client
│  └─ config/        Wiring và configuration kỹ thuật
├─ src/main/resources/
│  ├─ application.yml                 Default cấu hình không nhạy cảm
│  ├─ application-local.example.yml   Mẫu local được commit khi cần
│  ├─ application-local.yml           Cấu hình máy cá nhân, gitignore
│  └─ db/migration/                   Flyway của database service đó
├─ src/test/         Unit/integration test của service
├─ CONTEXT.md        Scope, ownership và bất biến ngắn của service
└─ pom.xml           Dependency chỉ của service
```

Không để service đọc database/schema của service khác. Local có thể dùng chung một PostgreSQL instance, nhưng database và user vẫn tách theo service.

## Tài liệu và nguồn chuẩn

| Vị trí | Dùng cho | Khi tạo/cập nhật |
| --- | --- | --- |
| `docs/architecture/` | Kiến trúc tổng, coding rules, roadmap | Khi nguyên tắc kiến trúc hoặc quy ước chung thay đổi |
| `docs/features/NNN-short-slug/` | Brief, design, plan cho một feature | Tạo trước khi code feature mới; cập nhật Plan khi hoàn tất |
| `docs/contracts/openapi/` | REST API public | Sửa trước hoặc cùng lúc khi endpoint/request/response đổi |
| `docs/contracts/events/` | Producer, consumer và schema Kafka event | Tạo/sửa trước khi event liên service đổi |
| `docs/adr/` | Quyết định dài hạn: boundary, data ownership, tech, port | Chỉ tạo khi có trade-off cần giữ lâu dài |
| `docs/STATUS.md` | Snapshot ngắn của dự án và việc kế tiếp | Cập nhật khi feature/phase hoàn tất hoặc hướng đi đổi |
| `apps/<service>/CONTEXT.md` | Scope và bất biến service | Chỉ sửa khi ownership/boundary của service đổi |
| `manual/` | Cách chạy, checklist, ghi chú cá nhân | Cập nhật khi thao tác vận hành thay đổi; không dùng làm contract/architecture |
| `AGENTS.md` và `.agents/skills/` | Router/workflow cho AI Agent | Chỉ sửa khi cách Agent cần tìm và tuân thủ nguồn chuẩn đổi |

Không tạo thêm tài liệu chỉ để chép lại nội dung từ nguồn chuẩn. Nếu một hướng dẫn vận hành cần nói về port, link đến ADR port thay vì tự ghi lại danh sách port.

## Hạ tầng, cấu hình và chạy local

| Vị trí | Nội dung |
| --- | --- |
| `infra/compose/compose.yaml` | PostgreSQL, Kafka, Redis và profile observability |
| `infra/compose/.env.example` | Mẫu biến môi trường Compose |
| `infra/compose/.env` | Giá trị local, gitignore |
| `manual/operations/local-runtime.md` | Các bước chạy/restart/kiểm tra hạ tầng local |
| `docs/adr/ADR-004-local-port-allocation.md` | Dải port V2 bắt buộc |
| `apps/<service>/src/main/resources/application-local.yml` | Path hoặc setting chỉ của máy local cho service đó |

Luồng vận hành local thông thường:

1. Chuẩn bị `.env` cho Compose theo hướng dẫn local runtime.
2. Khởi động hạ tầng Docker Compose.
3. Chạy các Spring Boot application cần dùng từ IntelliJ; mặc định chúng nạp profile `local`.
4. Kiểm tra health endpoint của service cần test.
5. Chạy E2E trong `tests/e2e/` hoặc mở file `.http` bằng IntelliJ.

Không commit `.env`, `application-local.yml`, database volume hay path cá nhân.

## Test nằm ở đâu

| Vị trí | Mục đích | Khi thêm |
| --- | --- | --- |
| `apps/<service>/src/test/` | Unit và integration test; persistence/API dùng Testcontainers | Mỗi behavior mới cần happy path và lỗi quan trọng |
| `tests/e2e/<service>/*.http` | Kịch bản E2E gọi service thật | Khi public API/flow giữa các API đổi |
| `tests/fixtures/` | File fixture nhỏ, deterministic cho E2E | Khi E2E cần dữ liệu filesystem có thể commit |

Chạy E2E theo [tests/e2e/README.md](../../tests/e2e/README.md). `.http` là kịch bản dùng chung; một file có hậu tố `.e2e.http` có thể dành riêng cho CLI khi cần khả năng mà IntelliJ HTTP Client không có, như polling tự động.

## Quy trình tạo một thay đổi mới

| Loại thay đổi | Tối thiểu cần làm |
| --- | --- |
| Sửa nội bộ một service, không đổi API/event/schema | Đọc `CONTEXT.md`, sửa code/test owner; không cần feature doc mới nếu chỉ là bug nhỏ |
| Feature nghiệp vụ mới | Tạo `docs/features/NNN-short-slug/` từ template, chốt Plan rồi mới code |
| Endpoint REST mới/đổi response | Cập nhật OpenAPI + code service + test/E2E bị ảnh hưởng |
| Kafka event mới/đổi schema | Cập nhật event contract + producer/consumer + test tương ứng |
| Table/index/schema mới | Thêm Flyway migration vào đúng service; không sửa migration đã chạy |
| Cấu hình local mới | Thêm vào `application-local.example.yml` của service nếu cần; giữ file thật ở local |
| Đổi port/công nghệ/ownership | Tạo hoặc cập nhật ADR trước; sau đó sửa architecture/context/code liên quan |
| Quy tắc Agent hoặc coding rule | Sửa owner duy nhất: `AGENTS.md`, `.agents/skills/` hoặc `docs/architecture/03-CODING_RULES.md` |
| Hướng dẫn thao tác cho bạn | Viết trong `manual/operations/`, `manual/checklists/` hoặc `manual/notes/` |

## Quy tắc format và commit

- Java được format bằng `./mvnw.cmd spotless:apply`; `mvn validate` tự kiểm tra format.
- Trước commit, chạy kiểm tra phù hợp với phạm vi thay đổi và `git diff --check`.
- Commit theo một mục đích rõ ràng: feature, fix, test, docs hoặc build/tooling. Không trộn refactor format với thay đổi nghiệp vụ nếu có thể tránh.
- Không stage thư mục `gemini/`; đó là tài liệu deep-dive cá nhân, không phải source of truth của dự án.

## Khi không biết bắt đầu từ đâu

1. Muốn hiểu dự án: đọc `README.md` rồi `docs/architecture/01-SUMMARY.md`.
2. Muốn sửa một service: mở `apps/<service>/CONTEXT.md`.
3. Muốn thêm feature: xem `docs/STATUS.md`, rồi tạo feature folder theo `docs/features/README.md`.
4. Muốn chạy hệ thống: mở `manual/operations/local-runtime.md`.
5. Muốn chạy API end-to-end: mở `tests/e2e/README.md`.
6. Muốn giao việc cho AI Agent: mở `manual/ai-agent/operating-guide.md`.
