# Trạng thái Backend V2

Updated: 2026-08-01

## Hiện tại

- Phase: Giai đoạn 0 — Bootstrap đã verified ở local.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: đã chốt monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Feature active: `002-catalog-vertical-slice` (Plan READY).

## Việc kế tiếp

1. Triển khai `002-catalog-vertical-slice` theo Plan READY.
2. Giữ guide local hiện hành khi thay đổi Compose/port/runtime.
3. Chỉ sau P1 mới quyết định feature Scan hoặc Kafka/outbox kế tiếp.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
