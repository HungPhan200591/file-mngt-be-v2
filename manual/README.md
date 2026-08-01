# Manual cá nhân

Thư mục này chứa tài liệu vận hành, checklist và ghi chú dành cho người dùng dự án.

- Không phải source of truth kiến trúc/contract.
- Không phải context mặc định của AI Agent.
- Agent chỉ đọc khi người dùng gọi tên file hoặc yêu cầu sửa tài liệu trong `manual/`.

Quy ước đặt tài liệu:

```text
manual/
├─ operations/   Chạy local, Docker, IntelliJ, thao tác vận hành
├─ ai-agent/     Cách người dùng giao việc và vận hành AI Agent
├─ checklists/   Checklist cá nhân theo việc
└─ notes/        Ghi chú học tập hoặc deep-dive cá nhân
```

Điểm bắt đầu để hiểu toàn bộ repository: [Bản đồ dự án Backend V2](./operations/project-map.md).

Tài liệu có chứa giá trị kỹ thuật cần chính xác (ví dụ port) phải link đến source of truth trong `docs/`, không tự trở thành source of truth mới. Quy tắc Agent thật vẫn nằm tại `AGENTS.md` và `.agents/skills/`.
