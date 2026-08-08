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
- Discovery dùng `walkFileTree` và queue bounded để stream tối đa 500.000 seen-item
  mỗi COPY segment; segment commit progress/checkpoint bằng lease fence đầu-cuối.
  Sau discovery, set-based staging diff chỉ đưa file mới, fingerprint đổi hoặc
  `MISSING` tái xuất hiện vào Java; finalization anti-join mark missing rồi dọn staging.
  Tập changed được materialize đúng một lần vào `scan_inventory_diff_stage` `UNLOGGED`;
  Java và SSE progress chỉ duyệt tập nhỏ này, không quét lại full staging theo page.
- Mỗi `scan_run` active có delayed deadline re-arm sau durable checkpoint; PostgreSQL
  timeout cục bộ chặn SQL/COPY giữ worker quá lease. Database vẫn là authority để
  conditional fail và lease fence chặn worker stale commit muộn.
- SSE `GET /api/v2/scans/{scanId}/events` chỉ là kênh best-effort process-local cho
  snapshot/progress/terminal aggregate. REST vẫn là source đọc trạng thái, proposal và
  issue; stream mất kết nối không được ảnh hưởng scan, browser tự REST-verify/fallback.
- Reconciliation đếm set-based tập changed một lần sau discovery. Chỉ SSE progress mang
  workload/count xử lý phase 2; dữ liệu này transient, không phải state nghiệp vụ durable.
- Reconciliation analyze chạy song song trên virtual thread với mức parallelism cấu hình
  (`scan.reconciliation-parallelism`, default 8). Commit DB vẫn single-thread trong
  `@Transactional(REQUIRES_NEW)`. Nếu bất kỳ partition fail, cancel tất cả partition còn lại.
- Reconciliation hot write path dùng PostgreSQL `COPY` trực tiếp cho `scan_proposal`/
  `scan_issue` và set-based `UPDATE ... FROM` + `INSERT ... SELECT` từ
  `scan_inventory_diff_stage` cho `scan_file_inventory`. Ba write cùng conditional
  checkpoint nằm trong một transaction chunk; JDBC/JPA batch không còn nằm trên hot path này.
- Môi trường study dùng PostgreSQL 18. UUID production của `scan-service` dùng UUIDv7;
  database default dùng native `uuidv7()`. FK proposal/issue → `scan_run` vẫn giữ.
- Resume sau process restart hoặc lease handoff chưa được hỗ trợ; run gián đoạn bị đánh
  `FAILED`. `REQUIRES_NEW` hiện chỉ bảo đảm atomicity/rollback của từng chunk.
- `scan_proposal` và `scan_issue` chỉ dùng PK index và unique constraint index phục vụ cả
  query lẫn insert; không tạo thêm index đơn cột hoặc composite trùng leading columns
  của unique constraint vì gây write amplification nghiêm trọng khi bulk insert.
- Approval ghi item và outbox cùng transaction.
- Dùng `platform/observability` cho direct-request correlation MDC; expose Prometheus chỉ trên direct
  service port và không dùng root/path/file name làm metric label.
- ECS JSON log không được ghi absolute scan root; ELK lỗi không được chặn preview/approval.

## Does not own

- Canonical subject/asset metadata.
- Thumbnail/GIF/hash processing.
