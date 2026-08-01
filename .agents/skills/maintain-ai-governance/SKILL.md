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
5. Giữ `AGENTS.md` dưới 80 dòng, `docs/STATUS.md` dưới 80 dòng và mỗi service context dưới 120 dòng. Nếu chi tiết hiếm dùng tăng lên, đặt thành reference một cấp từ owner/skill.
6. Kiểm tra link, tên skill router, line cap và whitespace trước khi bàn giao.

Không tạo changelog, wiki tổng hợp, snapshot code hoặc tài liệu chỉ lặp lại source code.
