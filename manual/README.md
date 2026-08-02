# Manual cá nhân

Thư mục này chứa tài liệu dành cho **chủ dự án đọc**: giải thích business, technical, database, cách vận hành và cách làm việc với AI.

## Quy định context AI Agent

> **Toàn bộ `manual/` không phải context mặc định của AI Agent.**

- AI Agent không được tự động nạp các file trong `manual/` khi coding.
- AI Agent chỉ đọc khi người dùng gọi đúng file hoặc yêu cầu tạo/sửa tài liệu manual.
- `manual/` không phải source of truth cho architecture, API, event hay coding rule.
- Source of truth thật nằm trong `docs/`, `apps/*/CONTEXT.md`, `AGENTS.md` và `.agents/skills/`.
- Kể cả [Hướng dẫn vận hành AI Agent](./ai-agent/operating-guide.md) cũng là tài liệu **cho người dùng đọc**, không phải rule mà Agent tự nạp.

## Bắt đầu nhanh

- Muốn hiểu dự án: [Hướng dẫn hiểu Backend V2](./learning/backend-v2/README.md).
- Muốn biết code/tài liệu nằm ở đâu: [Bản đồ dự án Backend V2](./operations/project-map.md).
- Muốn chạy hệ thống: [Vận hành Backend V2 ở local](./operations/local-runtime.md).
- Muốn xem metrics/log: [Quan sát Backend V2 ở local](./operations/observability-local.md).
- Muốn biết cách giao việc cho AI: [Hướng dẫn vận hành AI Agent](./ai-agent/operating-guide.md).

## Danh sách toàn bộ manual

### Hiểu Backend V2

- [Mục lục học Backend V2](./learning/backend-v2/README.md)
- [1. Business model](./learning/backend-v2/01-business-model.md) — Subject, Asset, JOKE, USE và Album.
- [2. Kiến trúc và technical concept](./learning/backend-v2/02-architecture-technical.md) — service ownership, Kafka, CQRS, outbox, Redis và Elasticsearch.
- [3. Use case và data flow](./learning/backend-v2/03-use-cases-data-flow.md) — use case hiện tại, FT013 và toàn bộ roadmap dự kiến.
- [4. Database Map chi tiết](./learning/backend-v2/04-database-map.md) — table, ID, transaction và một file đi xuyên database như thế nào.
- [5. FT013 primer](./learning/backend-v2/05-ft013-primer.md) — FT013 sẽ code gì và chưa làm gì.
- [6. Đọc flow bằng Grafana/Kibana](./learning/backend-v2/06-observability-scan-to-query.md) — lần theo một
  Scan E2E qua Scan, Catalog và Query.

### Vận hành dự án

- [Bản đồ dự án Backend V2](./operations/project-map.md) — folder/file nào lưu gì và khi nào cập nhật.
- [Vận hành Backend V2 ở local](./operations/local-runtime.md) — Docker, IntelliJ, service, health check và E2E.
- [Quan sát Backend V2 ở local](./operations/observability-local.md) — Prometheus, Grafana, ECS log và Kibana.
- [Docsify và GitHub Pages](./operations/docsify-github-pages.md) — xem local, deploy và xử lý 404.

### Làm việc với AI Agent

- [Hướng dẫn vận hành AI Agent](./ai-agent/operating-guide.md) — cách bắt đầu session, giao feature, review và commit.

Quy ước đặt tài liệu:

```text
manual/
├─ operations/   Chạy local, Docker, IntelliJ, thao tác vận hành
├─ ai-agent/     Cách người dùng giao việc và vận hành AI Agent
├─ learning/     Tài liệu giúp chủ dự án hiểu business và technical
├─ checklists/   Checklist cá nhân theo việc
└─ notes/        Ghi chú học tập hoặc deep-dive cá nhân
```

Tài liệu có chứa giá trị kỹ thuật cần chính xác (ví dụ port) phải link đến source of truth trong `docs/`, không tự trở thành source of truth mới. Quy tắc Agent thật vẫn nằm tại `AGENTS.md` và `.agents/skills/`.
