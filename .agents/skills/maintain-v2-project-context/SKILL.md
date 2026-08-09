---
name: maintain-v2-project-context
description: "Duy trì source of truth và snapshot quản trị riêng của Backend V2 gồm STATUS, TECHNICAL_DEBT, service CONTEXT, feature Plan, contract và ADR. Dùng sau khi global maintain-ai-governance đã chốt ownership, khi cần cập nhật hoặc audit context riêng của file_mngt_microservice; không dùng để chuẩn hóa common/global skills."
---

# Duy trì context Backend V2

1. Đọc `AGENTS.md`, `docs/STATUS.md` và đúng owner đang sửa; không nạp toàn bộ docs hay feature lịch sử.
2. Giữ ownership riêng của dự án:
   - Trạng thái hiện tại: `docs/STATUS.md`.
   - Nợ kỹ thuật đang mở: `docs/TECHNICAL_DEBT.md`.
   - Kiến trúc: `docs/architecture/`.
   - Owner nghiệp vụ: `apps/<service>/CONTEXT.md`.
   - Feature: `docs/features/<feature-id>/`.
   - Contract: `docs/contracts/`; quyết định dài hạn: `docs/adr/`.
3. Link tới owner; không sao chép rule, trạng thái hoặc quyết định sang nhiều file.
4. Xem `STATUS.md` và `TECHNICAL_DEBT.md` là snapshot: chỉ giữ work active/READY/deferred, gate và debt còn mở; xóa DONE/stale/trùng khi cập nhật đúng owner.
5. Plan `READY` phải có `Execution capsule` gồm owner, scope/files, must preserve và read on demand.
6. Chỉ giữ project-local skill có trigger hoặc domain knowledge riêng Backend V2. Common workflow phải gọi global skill bằng tên canonical.
7. Trước bàn giao, kiểm tra link, owner, router, stale path và source of truth bị cạnh tranh.
