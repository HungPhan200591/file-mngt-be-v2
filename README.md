# File Management Microservice V2

Backend V2 phục vụ hai mục tiêu: chuẩn hóa media data và học microservice/event-driven trên bài toán thật.

## Bắt đầu cho AI Agent

1. Đọc [AGENTS.md](./AGENTS.md).
2. Dùng [guide vận hành AI Agent](./manual/ai-agent/operating-guide.md) để gọi đúng workflow mà không phải nhắc lại toàn bộ rule.
3. Đọc [tóm tắt kiến trúc](./docs/architecture/01-SUMMARY.md).
4. Chọn đúng context service trong `apps/*/CONTEXT.md`.
5. Với feature mới, tạo docs theo [ADLC workflow](./docs/adlc/WORKFLOW.md) trước khi code.

Hướng dẫn chạy thủ công bằng IntelliJ và Docker: [Vận hành local](./manual/operations/local-runtime.md). Kịch bản E2E dùng chung cho IntelliJ và CLI: [E2E HTTP harness](./tests/e2e/README.md).

## Cấu trúc

```text
apps/       Năm service độc lập và context sở hữu
platform/   Event contract và test support được chia sẻ có kiểm soát
infra/      Docker Compose, observability và cấu hình local
docs/       Architecture, ADLC, contracts, ADR và feature docs
```

P0 đã được xác minh local với năm service Spring Boot và Compose. Implementation tiếp theo phải tuân thủ `AGENTS.md`.
