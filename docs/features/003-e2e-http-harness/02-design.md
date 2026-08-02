# 003 E2E HTTP harness — Design

Owner: platform test tooling
Brief: [01-brief.md](./01-brief.md)

## High Level Design

Diagram trả lời câu hỏi: Harness E2E HTTP được tổ chức và thực thi như thế nào để kiểm thử các microservice REST API endpoint đang chạy local?

```mermaid
flowchart TB
    DEV["<font color='white'>Developer / Agent</font>"] --> IDE["<font color='white'>IntelliJ HTTP Client<br/>(.http files)</font>"]
    DEV --> CLI["<font color='white'>httpyac CLI Runner<br/>(npm run e2e)</font>"]

    subgraph HARNESS["E2E HTTP Test Harness"]
        ENV["<font color='white'>Environment Config<br/>(http-client.env.json)</font>"]
        SUITE["<font color='white'>HTTP Test Scripts<br/>(tests/e2e/**/*.http)</font>"]
    end

    IDE --> HARNESS
    CLI --> HARNESS

    HARNESS -->|HTTP REST Requests| SERVICES["<font color='white'>Catalog / Scan / Query<br/>local service endpoints</font>"]
    SERVICES -->|Persist test records| DB[("<font color='white'>Service-owned PostgreSQL<br/>databases</font>")]

    style DEV fill:#4CAF50,stroke:#fff,stroke-width:2px
    style IDE fill:#2196F3,stroke:#fff,stroke-width:2px
    style CLI fill:#2196F3,stroke:#fff,stroke-width:2px
    style ENV fill:#4CAF50,stroke:#fff,stroke-width:2px
    style SUITE fill:#4CAF50,stroke:#fff,stroke-width:2px
    style SERVICES fill:#2196F3,stroke:#fff,stroke-width:2px
    style DB fill:#9C27B0,stroke:#fff,stroke-width:2px
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
