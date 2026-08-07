# Scan Service context

## Scope

Scan filesystem, parse filename/path và tạo proposal để review trước khi phát event sang Catalog.

## Owns

- Database `scan_db`: job, item, proposal, issue, inventory, staging reconciliation và outbox. Staging là scratch state `UNLOGGED`, không là source of truth.
- Strategy/registry parser theo root và region.
- Tích hợp `CatalogRegistryClient` gọi `catalog-service` lấy immutable `RegistrySnapshot` trước khi bắt đầu `scan_run`.
- API preview, review, approve/reject scan item.
- Event `media.file.discovered.v1` sau approval.

## Invariants

- Bắt buộc fetch thành công `RegistrySnapshot` từ Catalog trước khi tạo `scan_run`; nếu Catalog unavailable, trả 503 Service Unavailable.
- Preview không ghi Catalog, rename/move file hoặc xóa cache.
- JOKE dùng code; USE video/assets dùng normalized basename; USE Album dùng relative folder làm identity và có thể tạo candidate link `FULL_ALBUM_OF` tới Syncdroid để review.
- Parse mơ hồ tạo issue, không tự đoán.
- Warm scan ghi mọi seen-item vào staging nhưng chỉ rewrite inventory khi file mới, fingerprint đổi, hoặc `MISSING` tái xuất hiện; finalization lease-fenced dùng anti-join để mark missing rồi dọn staging.
- Approval ghi item và outbox cùng transaction.
- Dùng `platform/observability` cho direct-request correlation MDC; expose Prometheus chỉ trên direct
  service port và không dùng root/path/file name làm metric label.
- ECS JSON log không được ghi absolute scan root; ELK lỗi không được chặn preview/approval.

## Does not own

- Canonical subject/asset metadata.
- Thumbnail/GIF/hash processing.
