# Scan Service context

## Scope

Scan filesystem, parse filename/path và tạo proposal để review trước khi phát event sang Catalog.

## Owns

- Database `scan_db`: job, item, proposal, issue, inventory, staging reconciliation, outbox và review projection.
  Staging là scratch state `UNLOGGED`, không là source of truth.
- Strategy/registry parser theo root và region.
- Tích hợp `CatalogRegistryClient` gọi `catalog-service` lấy immutable `RegistrySnapshot` trước khi bắt đầu `scan_run`.
- Tích hợp `CatalogExistenceClient` gọi internal Catalog API sau parse changed candidate và trước persistence proposal.
- API preview, review, approve/reject scan item.
- Event `media.file.discovered.v2` sau approval. Target FT-059 thêm transactional
  `media.approval.shard.completed.v1` theo logical subject shard; contract đã `READY`, implementation pending.

## Invariants

- Chỉ rerun với `overwriteExisting=true` mới đối soát file biến mất và tạo `DELETE_ASSET`; normal scan không đánh dấu inventory `MISSING`.
- `DELETE_ASSET` luôn qua review/approve; Scan phát locator bằng `media.file.removed.v1`, không sở hữu hay suy đoán `subjectId`.

- Bắt buộc fetch thành công `RegistrySnapshot` từ Catalog trước khi tạo `scan_run`; nếu Catalog unavailable, trả 503 Service Unavailable.
- Existence lookup chia micro-batch tối đa 500, chạy ngoài transaction persistence và fail closed khi Catalog timeout/lỗi/protocol sai. `EXACT_ASSET_EXISTS` không ghi proposal; các classification khác giữ proposal cùng `catalogExistence` evidence. Không retry tự động; verification runtime còn deferred.
- Start preview hỗ trợ `overwriteExisting=true` cho rerun có chủ đích: materialize toàn bộ file
  hiện có vào reconciliation và giữ cả proposal `EXACT_ASSET_EXISTS`. Mặc định `false`
  vẫn changed-only; rerun không bypass review/approval.
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
- Reconciliation keyset reader hiện giới hạn `DIFF_PAGE_SIZE=25.000`; với workload 1M,
  đây là khoảng 40 page/chunk thực tế. `business-chunk-size=100.000` chỉ là upper bound
  hiện tại, không phải kích thước chunk hiệu dụng khi page reader nhỏ hơn. Benchmark không
  cho thấy gain latency rõ ràng sau khi giảm từ 100.000 xuống 25.000; lựa chọn này phục vụ
  bounded memory và giới hạn transaction.
- Reconciliation hot write path dùng PostgreSQL `COPY` trực tiếp cho `scan_proposal`/
  `scan_issue`. Inventory cold root được nhận diện một lần sau lease validation và ghi bằng
  `INSERT ... SELECT` từ `scan_inventory_diff_stage`; warm root giữ set-based
  `UPDATE ... FROM` + `INSERT ... SELECT` để bảo toàn changed/revived semantics. Ba write
  cùng conditional checkpoint nằm trong một transaction chunk; JDBC/JPA batch không còn nằm
  trên hot path này.
- Môi trường study dùng PostgreSQL 18. UUID production của `scan-service` dùng UUIDv7;
  database default dùng native `uuidv7()`. Hai FK hot path `scan_proposal`/`scan_issue`
  → `scan_run` được bỏ để COPY không lookup parent; giữ `scan_run_id` NOT NULL và unique
  constraint. FK `scan_decision`/`scan_outbox_event` → `scan_proposal` vẫn giữ `ON DELETE
  CASCADE`. Hiện không có lifecycle production xóa `scan_run`; nếu bổ sung phải dọn
  proposal/issue tường minh trước khi xóa run và audit orphan.
- Resume sau process restart hoặc lease handoff chưa được hỗ trợ; run gián đoạn bị đánh
  `FAILED`. `REQUIRES_NEW` hiện chỉ bảo đảm atomicity/rollback của từng chunk.
- `scan_proposal` và `scan_issue` chỉ dùng PK index và unique constraint index phục vụ cả
  query lẫn insert; không tạo thêm index đơn cột hoặc composite trùng leading columns
  của unique constraint vì gây write amplification nghiêm trọng khi bulk insert.
- Approval ghi item và outbox cùng transaction.
- Approval FT-059 phải route processing version mới theo canonical subject-key bucket, không theo proposal UUID;
  `completionShardCount` là durable work-unit count độc lập với bounded worker concurrency. Shard marker chỉ được
  ghi cùng transaction exact completion; global `APPROVAL_COMMITTED` vẫn chờ tổng mọi shard.
- Video proposal phát `assetRole=VIDEO`; Scan chỉ mô tả candidate và tags của file, không tự bầu primary.
  Catalog là owner election `PRIMARY_VIDEO` theo toàn bộ asset hiện có của subject.
- Danh sách proposal của một scan run lọc server-side theo `search` và `decision`; FE không lọc trên page hiện tại.
- `scan_run` lưu durable `changedFileCount` sau staging diff và `reconciledFileCount` sau từng checkpoint;
  history REST và SSE dùng chung hai counter này. Run cũ chưa có giá trị sẽ trả `null` cho tới khi chạy lại.
- Outbox relay dùng bounded continuous window: claim `SKIP LOCKED` đúng free slots, Kafka callback chỉ ghi
  completion queue và scheduler thread conditional-mark theo instance owner. Lease budget được validate lúc
  startup; broker failure mở breaker, owner mismatch được đo, và `OutboxPressureGate` chỉ pause bulk approval
  claim theo hysteresis. `SCAN_OUTBOX_CONTINUOUS_DRAIN_ENABLED=false` quay về legacy wave path. Duplicate sau
  crash/lease expiry vẫn được consumer dedupe theo `eventId`; metric không dùng path hoặc identity làm label.
- Review queue CQRS-lite dùng `scan_review_projection_root`, durable task và proposal/issue snapshot theo
  generation. Terminal finalize chỉ enqueue task O(1); projector root rebuild set-based ngoài Scan hot path,
  dùng lease + root generation fence và atomic pointer swap. Decision/reopen khóa cùng root watermark để giữ
  read-after-commit. Khi root/global projection chưa READY hoặc feature flag tắt, API fallback về historical
  query; projection không là source of truth.
- Targeted issue recheck (FT-038) là durable job theo `issueId`: worker claim lease ngắn bằng `SKIP LOCKED`,
  resolve lại path dưới configured root, phân tích một file và tạo observation/run mới. Không nhận absolute
  path, không walk toàn root; file mất được ghi nhận `MISSING`/`FILE_NOT_FOUND`. Status API, idempotency và
  lease-owner fencing của các state update vẫn là hardening debt.
- Bulk decision/reopen (FT-039) là durable job lưu `rootKey/search/action`, worker xử lý bounded batch qua
  `ScanReviewQueueDecisionBatch` trong transaction riêng, requeue khi còn candidate và hoàn tất khi batch rỗng.
  Claim dùng lease + `SKIP LOCKED`; selection hiện chưa snapshot cutoff nên candidate mới có thể lọt vào các batch
  sau, và status API/concurrent verification còn deferred.
- Dùng `platform/observability` cho direct-request correlation MDC; expose Prometheus chỉ trên direct
  service port và không dùng root/path/file name làm metric label.
- ECS JSON log không được ghi absolute scan root; ELK lỗi không được chặn preview/approval.

## Does not own

- Canonical subject/asset metadata.
- Thumbnail/GIF/hash processing.
