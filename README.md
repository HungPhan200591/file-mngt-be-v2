# File Management Microservice V2

Backend V2 phục vụ hai mục tiêu: chuẩn hóa media data và học microservice/event-driven trên bài toán thật.

## Bắt đầu cho AI Agent

1. Đọc [AGENTS.md](./AGENTS.md).
2. Dùng [guide vận hành AI Agent](./docs/AI_AGENT_GUIDE.md) để gọi đúng workflow mà không phải nhắc lại toàn bộ rule.
3. Đọc [tóm tắt kiến trúc](./docs/architecture/01-SUMMARY.md).
4. Chọn đúng context service trong `apps/*/CONTEXT.md`.
5. Với feature mới, tạo docs theo [ADLC workflow](./docs/adlc/WORKFLOW.md) trước khi code.

## Cấu trúc

```text
apps/       Năm service độc lập và context sở hữu
platform/   Event contract và test support được chia sẻ có kiểm soát
infra/      Docker Compose, observability và cấu hình local
docs/       Architecture, ADLC, contracts, ADR và feature docs
```

P0 đã bootstrap Maven multi-module, năm service Spring Boot tối thiểu và Compose local. Compile/test/Docker runtime chưa được chạy; implementation tiếp theo phải tuân thủ `AGENTS.md`.
