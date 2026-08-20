# Quy tắc Backend V2

## Bất biến toàn cục

- Một Agent chính tự thực hiện toàn bộ khảo sát, triển khai và review; không dùng sub-agent.
- Không tự chạy test/build, migration/import thật, xóa dữ liệu, reset repository, khởi động service, Docker Compose, git commit/push hoặc sinh sidebar/deploy docs nếu chưa được người dùng yêu cầu rõ ràng.
- File source tối đa 500 dòng; tách theo trách nhiệm, không tách vụn.
- Với Java mới/sửa, Agent bắt buộc khảo sát owner/package bị chạm trước khi code và tự audit/refactor ngay sau khi code theo `docs/architecture/03-CODING_RULES.md`; không bàn giao code vượt ngưỡng mà không tách hoặc ghi ngoại lệ.
- Service chỉ truy cập database của chính nó. Redis không là source of truth; Kafka không thay thế mọi HTTP call.
- Port local V2 bắt buộc theo `docs/adr/ADR-004-local-port-allocation.md`; không tự chọn port chuẩn hoặc port mới trước khi kiểm tra ADR và port đang listen.
- Docker Compose hạ tầng dùng `infra/compose/compose.yaml`, ứng dụng BE+FE dùng `infra/compose/compose.apps.yaml`. Sử dụng các lệnh `npm run docker:up`, `npm run docker:apps:up`, `npm run docker:stop <service>` để chuyển đổi 3 chế độ (Local Dev, Hybrid Debug, Full Docker) theo `manual/operations/docker-and-dev-modes.md`.
- Mặc định mọi nhắc đến **FE/frontend** là **FE V2** tại sibling repo `../file-mngt-fe-v2`; đọc `AGENTS.md` và đúng context module của repo đó trước khi khảo sát, lập feature hoặc code FE. Chỉ khi người dùng chỉ định rõ cần đối chiếu V1 mới dùng repo chỉ đọc: **BE V1** `../../v1/file-mngt-be-v1`; **FE V1** `../../v1/file-mngt-fe-v1`.
- Khi gợi ý câu lệnh CLI cho người dùng, luôn viết từ thư mục gốc dự án (dùng cờ prefix hoặc đường dẫn từ root); không bắt người dùng gõ lệnh `cd` chuyển thư mục.
- Context hygiene: Tuân thủ quy tắc bốc context tối thiểu. `docs/STATUS.md` và `docs/TECHNICAL_DEBT.md` chỉ là snapshot hiện tại; khi task hoàn tất, xóa debt đã trả và nội dung stale/trùng lặp. Khi kết thúc milestone/phase, chủ động xuất Handover Capsule (10–15 dòng) để người dùng mở session mới khi cần.

Mọi lệnh Maven của Agent dùng trực tiếp IntelliJ Project SDK `corretto-25` (JDK 25).

## Router đọc context

Các route dưới đây là lựa chọn loại trừ theo scope hiện tại, không phải checklist cộng dồn. Chỉ nạp skill và tài liệu
của route match rõ; không preload các route/feature khác để "phòng hờ".

1. Session mới, mất context, cần biết trạng thái dự án: dùng `$load-v2-context`; nếu skill chưa được nhận diện, đọc `.agents/skills/load-v2-context/SKILL.md`.
2. Feature mới hoặc thay đổi nghiệp vụ: dùng `$adlc-feature-delivery`; nếu skill chưa được nhận diện, đọc `.agents/skills/adlc-feature-delivery/SKILL.md`.
3. Đổi REST, Kafka event, database ownership, migration hoặc chạm từ hai service: dùng `$cross-service-contract`; nếu skill chưa được nhận diện, đọc `.agents/skills/cross-service-contract/SKILL.md`.
4. Thiết kế ownership/router dùng global `$maintain-ai-governance`; dọn rule/skill dùng global `$standardize-project-ai-context`; sửa source of truth riêng Backend V2 dùng `$maintain-v2-project-context`.
5. Tạo/sửa Mermaid: dùng global `$mermaid-styling`; không duy trì bản sao local.
6. Task cục bộ: đọc `docs/architecture/01-SUMMARY.md` và đúng một `apps/<service>/CONTEXT.md`; chỉ mở dependency trực tiếp.
7. Viết/review Java: đọc `docs/architecture/03-CODING_RULES.md` sau context owner. Nếu chạm API, configuration hoặc hành vi phụ thuộc version của framework/tool, dùng global `$find-docs` trước khi code; chỉ đọc contract/ADR khi task chạm đúng boundary.
8. Viết/chạy E2E HTTP: đọc `tests/e2e/README.md`; chỉ đọc feature/contract của API được kiểm tra.
9. Sửa Docsify/GitHub Pages hoặc khi người dùng yêu cầu preview/deploy docs: dùng `$deploy-github-pages`; trước khi khảo sát hoặc deploy phải đồng bộ remote bằng `git pull`; Agent chạy `node ./.docsify/generate-sidebar.mjs` ngay trước commit/push. Sidebar chỉ sinh từ `manual/` và `docs/`, không sửa tay.
10. Tạo/sửa trọn bộ deep-dive + summary + question bank: dùng `$study-topic-workflow`; nếu chỉ sửa một artifact, dùng đúng `$deep-dive-technical-topic`, `$distill-study-summary` hoặc `$build-question-bank`.
11. Refactor giữ nguyên hành vi, tách hàm/tách file hoặc cleanup Java: dùng `$refactor-spring-service`; chỉ đọc và cập nhật `docs/TECHNICAL_DEBT.md` khi task trả một debt đã đăng ký.
12. Tạo/sửa học liệu UC-xx, SC-xx, scenario hoặc Scale & Capacity: dùng `$study-use-case-scenario`; card/evidence ở `manual/learning/use-cases/`, deep-dive dùng chung ở `manual/learning/deep-dive/`.
13. Đánh giá release/cutover, service mới, async worker/scheduler, messaging pipeline hoặc major migration: dùng `$production-readiness-review` theo sáu pillar Well-Architected và control workflow nội bộ; không dùng mặc định cho diff nhỏ.
14. Liệt kê kiến thức đã học, active recall, mock hỏi-đáp hoặc đánh giá mức nắm một topic: dùng `$review-learning`; roadmap là owner của snapshot tiến độ học cá nhân.
15. Viết, tổ chức, phân tầng hoặc chuẩn hóa Unit/Integration/Benchmark Test cho Backend: dùng `$author-backend-tests`; nếu skill chưa được nhận diện, đọc `.agents/skills/author-backend-tests/SKILL.md`.


Không đọc toàn bộ plan, context hay feature lịch sử. Chi tiết về ADLC, contract và session nằm trong skill tương ứng.

## Source of truth

- Kiến trúc: `docs/architecture/`.
- Trạng thái hiện tại: `docs/STATUS.md`.
- Nợ kỹ thuật/refactor backlog: `docs/TECHNICAL_DEBT.md`.
- Context nghiệp vụ/owner: `apps/<service>/CONTEXT.md`.
- Feature đang làm: `docs/features/<feature-id>/`.
- REST và Kafka contract: `docs/contracts/`.
- Quyết định dài hạn: `docs/adr/`.
- Tài liệu cá nhân & học tập: `manual/` không phải source of truth hay context mặc định; chỉ đọc khi người dùng gọi tên hoặc yêu cầu sửa. Khi triển khai task/code, Agent chỉ nạp context capsule tối thiểu được router chỉ định, tuyệt đối không tự ý nạp các tài liệu giải thích chi tiết, deep-dive hay bài review lớn trong `manual/` để tránh tốn token và loãng context.

Khi đổi contract hoặc boundary, cập nhật source of truth tương ứng trong cùng task; không sao chép rule sang nhiều nơi.

Trước bàn giao, audit source of truth: nếu architecture, contract, ownership hoặc rule đổi thì cập nhật đúng owner trong cùng task; nếu không đổi, nêu rõ không cần cập nhật tài liệu. Đồng thời distill `STATUS.md`/`TECHNICAL_DEBT.md`: chỉ giữ việc active/READY/deferred, gate hoặc debt còn mở; xóa history đã DONE. Luôn đề xuất 1–3 việc tiếp theo theo mức ưu tiên, nhưng không tự mở rộng phạm vi hoặc làm tiếp khi chưa được yêu cầu.
