---
name: load-v2-context
description: Nạp lại bối cảnh Backend V2 khi bắt đầu session mới, mất context, người dùng nói tiếp tục hoặc hỏi trạng thái. Dùng để đọc router, trạng thái hiện tại, kiến trúc và đúng context service liên quan trước khi làm việc; không sửa code, không chạy build hay service.
---

# Nạp context Backend V2

1. Đọc `AGENTS.md`, `docs/STATUS.md`, `docs/architecture/01-SUMMARY.md`.
2. Kiểm tra `git status --short` nếu repository đã được khởi tạo.
3. Nếu task đã rõ owner, đọc đúng `apps/<service>/CONTEXT.md`; nếu chưa rõ, chỉ báo map service, không đọc tất cả context.
4. Nếu có feature active được nêu tên, đọc folder feature đó và contract trực tiếp liên quan.
5. Trả tóm tắt tối đa: phase hiện tại, thay đổi local, owner/task tiếp theo, blocker nếu có.

Không sửa file, không chạy Maven/Docker/migration và không tự suy ra feature cần triển khai.
