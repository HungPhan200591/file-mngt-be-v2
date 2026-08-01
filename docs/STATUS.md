# Trạng thái Backend V2

Updated: 2026-08-01

## Hiện tại

- Phase: chuẩn bị Giai đoạn 0 — Bootstrap.
- Code: chưa bootstrap Maven, Java service hoặc Docker Compose.
- Kiến trúc: đã chốt monorepo 5 service, PostgreSQL, Kafka, Redis và ADLC.
- Feature active: `001-bootstrap-platform` (sẵn sàng code, chưa có source).

## Việc kế tiếp

1. Triển khai `001-bootstrap-platform` khi người dùng yêu cầu code bootstrap.
2. Chạy kiểm tra build/Docker theo Plan khi được người dùng cho phép.
3. Sau P0, tạo feature `002-catalog-vertical-slice`.

## Bất biến cần nhớ

- V1 chạy song song, không bị sửa hoặc xóa bởi V2.
- Catalog là canonical write model; Query chỉ là projection.
- Scan tạo proposal, Worker xử lý nền, Gateway không chứa domain logic.
