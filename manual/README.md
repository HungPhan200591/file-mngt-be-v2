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

- Muốn hiểu dự án: [Hướng dẫn Nhập môn Hệ thống (System Primer)](./learning/system-primer/README.md).
- Muốn biết code/tài liệu nằm ở đâu: [Bản đồ dự án Backend V2](./operations/project-map.md).
- Muốn chạy hệ thống: [Vận hành Backend V2 ở local](./operations/local-runtime.md).
- Muốn xem metrics/log: [Quan sát Backend V2 ở local](./operations/observability-local.md).
- Muốn biết cách giao việc cho AI: [Hướng dẫn vận hành AI Agent](./ai-agent/operating-guide.md).

## Danh sách toàn bộ manual

### Nhập môn Hệ thống (System Primer)

- [Mục lục Nhập môn Hệ thống](./learning/system-primer/README.md)
- [1. Business model](./learning/system-primer/01-business-model.md) — Subject, Asset, JOKE, USE và Album.
- [2. Kiến trúc Tổng quan](./learning/system-primer/02-architecture-overview.md) — service ownership, Kafka, CQRS, outbox, Redis và Elasticsearch.
- [3. Use case và data flow](./learning/system-primer/03-use-cases-data-flow.md) — use case hiện tại, FT013 và toàn bộ roadmap dự kiến.
- [4. Database Map chi tiết](./learning/system-primer/04-database-map.md) — table, ID, transaction và một file đi xuyên database như thế nào.
- [5. FT013 primer](./learning/system-primer/05-ft013-primer.md) — FT013 sẽ code gì và chưa làm gì.
- [6. Observability Flow Overview](./learning/system-primer/06-observability-overview.md) — lần theo một Scan E2E qua Scan, Catalog và Query.
- [7. API Flows Overview](./learning/system-primer/07-api-flows-overview.md) — các luồng REST API chính theo hành trình người dùng.

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
