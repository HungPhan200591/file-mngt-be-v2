---
name: load-v2-context
description: Nạp nhanh bối cảnh Backend V2 theo cơ chế Lazy Targeted Capsule khi bắt đầu session mới hoặc mất context. Tuyệt đối không đọc raw toàn bộ STATUS/SUMMARY; chỉ nạp lát cắt tối thiểu theo task.
---

# Nạp context Backend V2 (Lazy Targeted Capsule)

1. **Nếu người dùng đã chỉ định feature/task** (ví dụ FT-xxx, BT-xxx, service Y):
   - Bỏ qua `STATUS.md` và `01-SUMMARY.md`.
   - Đọc trực tiếp `docs/features/<feature-id>/03-plan.md` hoặc `apps/<service>/CONTEXT.md` liên quan.
2. **Nếu chưa có task cụ thể hoặc hỏi trạng thái tổng quan**:
   - Dùng `grep_search` hoặc slice chỉ đọc section `## Trọng tâm ưu tiên tối đa hiện tại` và `## Việc tiếp theo` trong `docs/STATUS.md` (không đọc toàn bộ file).
   - Kiểm tra `git status --short`.
3. **Không đọc lại `AGENTS.md`** (đã có trong prompt nền).
4. **Trả capsule 5–8 dòng**: Trọng tâm hiện tại, thay đổi local, owner/task tiếp theo, blocker nếu có.

Không sửa file, không chạy build/migration/Docker và không nạp tài liệu thừa.
