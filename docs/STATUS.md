# Trạng thái Backend V2

Updated: 2026-08-01

## Hiện tại

- Phase: Giai đoạn 0 — Bootstrap source hoàn tất; chờ kiểm tra runtime.
- Code: Maven multi-module, Maven Wrapper 3.9.16, năm Spring Boot app tối thiểu, event envelope và Docker Compose PostgreSQL/Kafka/Redis đã có.
- Kiến trúc: đã chốt monorepo 5 service, PostgreSQL tách database/user theo service, Kafka, Redis và ADLC.
- Feature active: không có; `001-bootstrap-platform` đã DONE.

## Việc kế tiếp

1. Cài/chọn JDK 25 rồi chạy kiểm tra build/Docker theo Plan khi được người dùng cho phép.
2. Tạo feature `002-catalog-vertical-slice`.
3. Chỉ sau khi P0 được xác minh mới thêm Flyway và database logic vào Catalog.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
