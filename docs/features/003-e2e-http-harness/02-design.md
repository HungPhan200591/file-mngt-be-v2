# 003 E2E HTTP harness — Design

Owner: platform test tooling
Brief: [01-brief.md](./01-brief.md)

## High Level Design

Diagram trả lời câu hỏi: Harness E2E HTTP được tổ chức và thực thi như thế nào để kiểm thử các microservice REST API endpoint đang chạy local?

```mermaid
flowchart TB
    DEV["Developer / Agent"] --> IDE["IntelliJ HTTP Client<br/>(.http files)"]
    DEV --> CLI["httpyac CLI Runner<br/>(npm run e2e)"]

    subgraph HARNESS["E2E HTTP Test Harness"]
        ENV["Environment Config<br/>(http-client.env.json)"]
        SUITE["HTTP Test Scripts<br/>(tests/e2e/**/*.http)"]
    end

    IDE --> HARNESS
    CLI --> HARNESS

    HARNESS -->|HTTP REST Requests| SERVICES["Catalog / Scan / Query<br/>local service endpoints"]
    SERVICES -->|Persist test records| DB[("Service-owned PostgreSQL<br/>databases")]

    style DEV fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style IDE fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style CLI fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style ENV fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style SUITE fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style SERVICES fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style DB fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
```

## Quyết định

- Dùng JetBrains-style `.http` và `httpYac` CLI `6.16.7` pin trong `tests/e2e/package.json`.
- `tests/e2e/README.md` là hướng dẫn vận hành duy nhất cho E2E; `AGENTS.md` chỉ route tới tài liệu này khi task có E2E.
- `tests/e2e/http-client.env.example.json` được commit; `http-client.env.json` chỉ local.
- Mỗi API owner có folder riêng. Request đặt tên, assertion `??`, và dùng data `E2E-` để cô lập dữ liệu.

## Giới hạn

- E2E kiểm tra runtime đã được người dùng khởi động; Testcontainers Maven vẫn là integration test tự động độc lập.
- Catalog hiện không có delete API nên E2E create sẽ để lại subject `E2E-*` ở local DB. Không dùng scenario này với dữ liệu production.
- Không có contract/architecture/data ownership thay đổi.
