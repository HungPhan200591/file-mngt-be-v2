# 002 Catalog vertical slice

Owner: `catalog-service`

## Vấn đề

V2 chưa có canonical data model hay API để lưu một video/album và asset vật lý thuộc nó. Các phase Scan, Worker, Query không thể có contract đúng nếu Catalog chưa có một vertical slice nhỏ, chạy được và sở hữu `catalog_db` rõ ràng.

## Mục tiêu và acceptance criteria

- Flyway của Catalog tạo `media_subject` và `media_asset` trong `catalog_db`, không có cross-database access.
- `media_subject` hỗ trợ `VIDEO` và `ALBUM`; identity duy nhất là `(region, subject_type, identity_key)`.
- API Catalog v1 tạo subject kèm asset ban đầu, lấy detail theo UUID và liệt kê phân trang/filter theo `region`/`subjectType`.
- Duplicate identity trả `409`; validation trả `400`; subject không tồn tại trả `404` theo OpenAPI contract.
- Asset có `role` và `relativePath`; mỗi subject tối đa một `PRIMARY_VIDEO` ở mức database.
- Có integration test dùng Testcontainers PostgreSQL cho migration, create, unique conflict và list pagination.

## Ngoài phạm vi

- Kafka, outbox relay, consumer, Redis, Elasticsearch và Query projection.
- Scan filesystem, parser JOKE/USE, Worker media processing.
- Actress, Studio, Tag, alias, search mờ, edit/delete, authentication và UI/frontend.
- Import dữ liệu V1 hoặc thay đổi database V1.

## Câu hỏi/rủi ro mở

- Không có quyết định kiến trúc mở. `identity_key` là canonical key do caller cung cấp trong P1; Scan parser sẽ tạo key chuẩn theo rule JOKE/USE ở feature riêng.
- Code chỉ bắt đầu sau khi P0 được kiểm tra runtime với JDK 25 của IntelliJ theo `docs/STATUS.md`.
