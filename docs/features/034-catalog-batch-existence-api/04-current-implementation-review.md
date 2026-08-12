# FT-034 — Review triển khai hiện tại

`CatalogScanExistenceController` validate toàn request 1–500 item và `clientRef` unique. Classifier chạy read-only `REPEATABLE_READ`, read-store lookup set-based locator/subject, trả đúng một classification cho mỗi `clientRef`. Endpoint không ghi subject/asset/outbox và không truy cập `scan_db`; Catalog vẫn là authority canonical. Flyway V8 tạo unique partial index `(storage_key, relative_path)` cho locator non-null; duplicate phải làm migration fail.

Khi FT-035 gọi endpoint, `400`, `503`, timeout hoặc response thiếu/duplicate/unknown classification đều phải fail closed trước transaction persistence của Scan. Existence snapshot không reserve locator; approval consumer và unique constraint xử lý race phát sinh sau lookup.

Verification còn thiếu: Flyway/Testcontainers, query-count batch 500, primary-role conflict, timeout/retry read-only và service-to-service auth/network. Không dùng FT-034 để kết luận SC-01 đã scale 1M.
