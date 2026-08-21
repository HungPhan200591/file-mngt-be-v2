# FT-056 — BT-09D2 Catalog Set-Based CTE Merge

Owner: `catalog-service`

## Vấn đề

FT-055 đã khóa ingest: durable stage và workset có mặt trước finalizer. Điểm nghẽn tiếp theo nằm trong
`catalog_finalize_operation_page` (V19): mỗi subject page tạo 7 temp table, 4 index phụ và 5 `ANALYZE`.
Baseline FT-054 ghi ~`144 ms/page` × 64 page ≈ `9.2 s` chỉ vì DDL catalog lock, chưa kể `catalog_subject_state_json`
cho change-detection. Worker Java vẫn claim-per-page (BT-09D3) và relay (BT-09D4) nằm ngoài lát này.

`STATUS.md` từng gọi D2 là "Watermark Gate" — đó là nhầm với mục [D2] lịch sử của FT-054. Lát BT-09D2
là **canonical SQL merge**, không đổi equality/watermark.

## Mục tiêu và acceptance criteria

- Viết lại `catalog_finalize_operation_page` bằng in-query set-based CTE; vòng page **không** còn
  `CREATE TEMPORARY TABLE`, `CREATE INDEX` hay `ANALYZE`.
- Subject mới (`media_subject` chưa tồn tại lúc page bắt đầu): không gọi `catalog_subject_state_json` để
  tính `before_hash`; luôn `changed = true`; `version` giữ `0`.
- Subject đã có: so `before_hash`/`after_hash`; chỉ tăng version đúng một lần và insert outbox khi aggregate đổi.
- Giữ nguyên fence `owner + fence_token + lease_until`, page size hiện hành, primary election, tag từ primary,
  tombstone locator, actress/registry bump một lần mỗi page có actress mới, unique outbox
  `(operationId, subjectId, eventType)`, `SUBJECT_SNAPSHOT_TOO_LARGE` → operation `BLOCKED`,
  checkpoint workset đúng cardinality.
- Gate calibration: sau warm-up, `pageExec` median `< 5 ms` trên page size production hiện tại (`500`,
  hoặc giá trị đã khóa trong run manifest).
- Gate qualification: profile `100.000 subjects × 10 assets` (1M staged events đã ingest sẵn, **không** nằm
  trong đồng hồ D2); wall-clock merge từ `READY_TO_COALESCE` tới mọi workset `COMPLETED` `<= 5.000 ms`.
- Isolated merge benchmark dùng Spring finalizer/page store thật; không tự viết runner thay `catalog_finalize_operation_page`.
- Có IT parity: subject mới, subject cũ không đổi, subject cũ đổi, primary election, tags, tombstone,
  actress/registry, snapshot quá lớn, fence loss, cardinality mismatch.
- Telemetry page tách `acquire` khỏi `pageExec`; report D2 chỉ dùng `pageExec` + wall-clock merge.
- Không log payload, absolute path, identity hay secret.

## Ngoài phạm vi

- Continuous lane drain / một claim nhiều page (BT-09D3).
- Catalog outbox relay và Catalog 1M gate (BT-09D4).
- Equality gate / watermark `media.approval.watermark.v1`.
- Typed ingest path (FT-055) và event contract `media.file.discovered.v2` / `media.subject.changed.v2`.
- Query projection, `QUERY_DB_READY`, `SLI-03`.

## Câu hỏi/rủi ro mở

- `5 ms/page` là gate hợp đồng từ break-task, chưa có run CTE. Nếu trượt: ghi evidence, dừng; không tự
  đổi page size hay claim loop thành D3.
- CTE `MATERIALIZED` có thể spill work file; qualification phải ghi `work_mem` và không dùng temp DDL để "né".
- Profile 1M cold gần như toàn subject mới — bypass hash giúp D2; profile update/existing vẫn trả giá
  `catalog_subject_state_json` và phải có test parity, không lấy cold làm chứng minh mọi workload.
