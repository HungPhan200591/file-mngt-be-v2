---
name: maintain-ai-governance
description: Duy trì hệ thống context, rule, template và skill của Backend V2 để Agent đọc ít nhưng đúng nguồn chuẩn. Dùng khi tạo hoặc sửa AGENTS.md, docs/STATUS.md, service CONTEXT.md, ADLC template, contract router hoặc project-local skill; ngăn tài liệu trùng lặp và context phình to.
---

# Duy trì AI governance

1. Đọc `AGENTS.md`, `docs/STATUS.md` và owner đang sửa; không nạp toàn bộ docs lịch sử.
2. Xác định một source of truth trước khi viết:
   - Router/bất biến toàn dự án: `AGENTS.md`.
   - Trạng thái session: `docs/STATUS.md`.
   - Kiến trúc: `docs/architecture/`.
   - Owner nghiệp vụ: `apps/<service>/CONTEXT.md`.
   - Feature: `docs/features/<feature-id>/`.
   - Contract: `docs/contracts/`; quyết định dài hạn: `docs/adr/`.
3. Link thay vì copy rule. Khi di chuyển owner, xóa tham chiếu cũ trong cùng task.
4. Chỉ tạo skill khi có workflow lặp lại, rủi ro cao hoặc cần trigger riêng; body skill giữ dưới 120 dòng.
5. Giữ `AGENTS.md` dưới 80 dòng, `docs/STATUS.md` dưới 80 dòng và mỗi service context dưới 120 dòng. `AGENTS.md` chỉ giữ bất biến, router và source of truth toàn cục; workflow chi tiết chuyển vào skill, rule mới phải gộp/thay thế rule cũ thay vì append.
6. `STATUS.md` và `TECHNICAL_DEBT.md` là snapshot thay thế, không append lịch sử: chỉ giữ work active/READY/deferred, gate hoặc debt còn mở; xóa trạng thái/debt đã hoàn tất, route stale và rule trùng lặp. Evidence nằm ở Plan/commit.
7. Plan `READY` phải có `Execution capsule` đủ owner, scope/files, must preserve và read on demand.
8. Kiểm tra link, tên skill router, line cap và whitespace trước khi bàn giao.

Không tạo changelog, wiki tổng hợp, snapshot code hoặc tài liệu chỉ lặp lại source code.
