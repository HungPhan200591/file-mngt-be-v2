# File Management Microservice V2

Backend V2 phục vụ hai mục tiêu: chuẩn hóa media data và học microservice/event-driven trên bài toán thật.

## Bắt đầu cho chủ dự án

- [Manual cá nhân](./manual/README.md): mục lục đọc trên GitHub Mobile — business, technical, database, data flow, vận hành và cách làm việc với AI.
- [Bản đồ dự án](./manual/operations/project-map.md): code/tài liệu nằm ở đâu.
- [Vận hành local](./manual/operations/local-runtime.md): IntelliJ, Docker và E2E.
- [Quan sát local](./manual/operations/observability-local.md): Prometheus, Grafana, ECS logs và Kibana.

`manual/` chỉ dành cho người dùng đọc; không phải source of truth và không phải context mặc định của AI Agent.

## Bắt đầu cho AI Agent

1. Đọc [AGENTS.md](./AGENTS.md).
2. Đọc [tóm tắt kiến trúc](./docs/architecture/01-SUMMARY.md).
3. Chọn đúng context service trong `apps/*/CONTEXT.md`.
4. Với feature mới, tạo docs theo [ADLC workflow](./docs/adlc/WORKFLOW.md) trước khi code.

Kịch bản E2E dùng chung cho IntelliJ và CLI: [E2E HTTP harness](./tests/e2e/README.md).

## Cấu trúc

```text
apps/       Năm service độc lập và context sở hữu
platform/   Event contract, observability và test support được chia sẻ có kiểm soát
infra/      Docker Compose, observability và cấu hình local
docs/       Architecture, ADLC, contracts, ADR và feature docs
```

P0 đã được xác minh local với năm service Spring Boot và Compose. Implementation tiếp theo phải tuân thủ `AGENTS.md`.
