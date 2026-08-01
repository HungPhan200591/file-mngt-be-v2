# File Management Microservice V2

Backend V2 phục vụ hai mục tiêu: chuẩn hóa media data và học microservice/event-driven trên bài toán thật.

## Bắt đầu cho AI Agent

1. Đọc [AGENTS.md](./AGENTS.md).
2. Đọc [tóm tắt kiến trúc](./docs/architecture/01-SUMMARY.md).
3. Chọn đúng context service trong `apps/*/CONTEXT.md`.
4. Với feature mới, tạo docs theo [ADLC workflow](./docs/adlc/WORKFLOW.md) trước khi code.

## Cấu trúc

```text
apps/       Năm service độc lập và context sở hữu
platform/   Event contract và test support được chia sẻ có kiểm soát
infra/      Docker Compose, observability và cấu hình local
docs/       Architecture, ADLC, contracts, ADR và feature docs
```

Mã nguồn chưa được bootstrap ở repository này. Tài liệu là contract khởi đầu; implementation Agent phải tuân thủ `AGENTS.md`.
