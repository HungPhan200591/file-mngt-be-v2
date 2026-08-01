# Quy tắc Backend V2

## Bất biến toàn cục

- Giao tiếp và tài liệu viết tiếng Việt có dấu; giữ identifier/API/event/package bằng tiếng Anh.
- Một Agent chính tự thực hiện toàn bộ khảo sát, triển khai và review; không dùng sub-agent.
- Không tự chạy migration/import thật, xóa dữ liệu, reset repository, khởi động service hoặc Docker Compose nếu chưa được yêu cầu rõ ràng.
- File source tối đa 500 dòng; tách theo trách nhiệm, không tách vụn.
- Service chỉ truy cập database của chính nó. Redis không là source of truth; Kafka không thay thế mọi HTTP call.
- Port local V2 bắt buộc theo `docs/adr/ADR-004-local-port-allocation.md`; không tự chọn port chuẩn hoặc port mới trước khi kiểm tra ADR và port đang listen.

## Router đọc context

1. Session mới, mất context, cần biết trạng thái dự án: dùng `$load-v2-context`; nếu skill chưa được nhận diện, đọc `.agents/skills/load-v2-context/SKILL.md`.
2. Feature mới hoặc thay đổi nghiệp vụ: dùng `$adlc-feature-delivery`; nếu skill chưa được nhận diện, đọc `.agents/skills/adlc-feature-delivery/SKILL.md`.
3. Đổi REST, Kafka event, database ownership, migration hoặc chạm từ hai service: dùng `$cross-service-contract`; nếu skill chưa được nhận diện, đọc `.agents/skills/cross-service-contract/SKILL.md`.
4. Tạo/sửa rule, context, template, skill hoặc router tài liệu: dùng `$maintain-ai-governance`; nếu skill chưa được nhận diện, đọc `.agents/skills/maintain-ai-governance/SKILL.md`.
5. Task cục bộ: đọc `docs/architecture/01-SUMMARY.md` và đúng một `apps/<service>/CONTEXT.md`; chỉ mở dependency trực tiếp.
6. Viết/review Java: đọc `docs/architecture/03-CODING_RULES.md` sau context owner; chỉ đọc contract/ADR khi task chạm đúng boundary.
7. Viết/chạy E2E HTTP: đọc `tests/e2e/README.md`; chỉ đọc feature/contract của API được kiểm tra.

Không đọc toàn bộ plan, context hay feature lịch sử. Chi tiết về ADLC, contract và session nằm trong skill tương ứng.

## Source of truth

- Kiến trúc: `docs/architecture/`.
- Trạng thái hiện tại: `docs/STATUS.md`.
- Context nghiệp vụ/owner: `apps/<service>/CONTEXT.md`.
- Feature đang làm: `docs/features/<feature-id>/`.
- REST và Kafka contract: `docs/contracts/`.
- Quyết định dài hạn: `docs/adr/`.
- Tài liệu cá nhân: `manual/` không phải source of truth hay context mặc định; chỉ đọc khi người dùng gọi tên hoặc yêu cầu sửa.

Khi đổi contract hoặc boundary, cập nhật source of truth tương ứng trong cùng task; không sao chép rule sang nhiều nơi.

Trước bàn giao, audit source of truth: nếu architecture, contract, ownership hoặc rule đổi thì cập nhật đúng owner trong cùng task; nếu không đổi, nêu rõ không cần cập nhật tài liệu. Luôn đề xuất 1–3 việc tiếp theo theo mức ưu tiên, nhưng không tự mở rộng phạm vi hoặc làm tiếp khi chưa được yêu cầu.
