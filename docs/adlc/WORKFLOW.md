# ADLC workflow

ADLC trong repository này là quy trình phát triển do AI hỗ trợ nhưng tài liệu là contract trước khi code.

## Luồng chuẩn

1. **Brief** — vấn đề, phạm vi, acceptance criteria, out-of-scope.
2. **Design** — service owner, model, API/event, data ownership, performance và failure mode.
3. **Plan** — thứ tự file/module, migration, verification, rollout/rollback.
4. **Implement** — Agent chỉ làm theo plan `READY`.
5. **Verify** — static/integration/contract/manual tùy phạm vi.
6. **Close** — cập nhật Plan, ADR hoặc contract còn hiệu lực.

## Feature folder

```text
docs/features/<feature-id>/
├─ 01-brief.md
├─ 02-design.md
└─ 03-plan.md
```

Dùng template tương ứng trong `docs/templates/`. Feature chỉ nằm một folder; không copy cùng rule vào context service.

## Trạng thái tối giản

- Chỉ `03-plan.md` có `Status`.
- `DRAFT`: tài liệu hoặc quyết định chưa đủ để code.
- `READY`: đủ để Agent triển khai trong phạm vi đã ghi.
- `DONE`: đã bàn giao; Plan ghi ngắn kiểm tra đã chạy và phần còn chờ người dùng.

Brief/Design không có status vì trạng thái của chúng được suy ra từ Plan. Feature bị thay thế chỉ cần thêm link `Replaced by` vào Plan hoặc ADR liên quan.

## Khi cần ADR

Tạo ADR khi thay service boundary, ownership dữ liệu, event contract, công nghệ nền tảng hoặc trade-off dài hạn. Không tạo ADR cho layout package, đổi tên cơ học hoặc bug fix cục bộ.
