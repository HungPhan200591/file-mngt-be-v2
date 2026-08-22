# FT-056 — BT-09D2 Catalog Set-Based CTE Merge

Owner: `catalog-service`

## Vấn đề

FT-055 đã khóa ingest: durable stage và workset có mặt trước finalizer. Điểm nghẽn tiếp theo nằm trong
`catalog_finalize_operation_page` (V19): mỗi subject page tạo 7 temp table, 4 index phụ và 5 `ANALYZE`.
Baseline FT-054 ghi ~`144 ms/page` × 64 page ≈ `9.2 s` chỉ vì DDL catalog lock, chưa kể `catalog_subject_state_json`
cho change-detection. Worker Java vẫn claim-per-page (BT-09D3) và relay (BT-09D4) nằm ngoài lát này.

`STATUS.md` từng gọi D2 là "Watermark Gate" — đó là nhầm với mục [D2] lịch sử của FT-054. Lát BT-09D2
là **canonical SQL merge**, không đổi equality/watermark.

## Evidence mới và mục tiêu phục hồi

Run do người dùng báo ngày 2026-08-22 xác nhận V21 vẫn chậm hơn V19 ở calibration và workload 1M tiếp tục
timeout. Repo chưa có log số liệu chi tiết của V21, vì vậy chỉ ghi nhận kết luận định tính; không suy diễn
`mergeMs` hay throughput. V20/V21 được coi là candidate thất bại và giữ immutable.

Mục tiêu gần nhất là **recovery gate**: hoàn tất canonical merge cho `100.000 subjects × 10 assets`
(1M staged events) trong **dưới 60 giây**, không timeout/gãy connection và không chuyển chi phí sang bước seed
ngoài đồng hồ. Gate lịch sử `<= 5 giây` vẫn là stretch target của BT-09D2, chưa bị tuyên bố đạt hay xóa.

## Mục tiêu và acceptance criteria

- Viết lại `catalog_finalize_operation_page` bằng direct set-based merge từ durable typed reduction; vòng page
  **không** còn temp DDL/INDEX/ANALYZE, global scratch copy/delete hay scan raw stage trên hot path.
- Subject mới (`media_subject` chưa tồn tại lúc page bắt đầu): không gọi `catalog_subject_state_json` để
  tính `before_hash`; luôn `changed = true`; `version` giữ `0`.
- Subject đã có: so `before_hash`/`after_hash`; chỉ tăng version đúng một lần và insert outbox khi aggregate đổi.
- Giữ nguyên fence `owner + fence_token + lease_until`, page size hiện hành, primary election, tag từ primary,
  tombstone locator, actress/registry bump một lần mỗi page có actress mới, unique outbox
  `(operationId, subjectId, eventType)`, `SUBJECT_SNAPSHOT_TOO_LARGE` → operation `BLOCKED`,
  checkpoint workset đúng cardinality.
- Trước khi đổi thuật toán, có phase evidence cho page acquisition, stage/reduction read, canonical subject,
  asset/tag, metadata/primary, snapshot/outbox và checkpoint; kèm buffer/temp/WAL evidence đủ phân biệt CPU,
  I/O, sort/spill, lock và scratch-table churn.
- Không tiếp tục copy raw JSONB qua chuỗi global UNLOGGED scratch rồi `DELETE` lại mỗi page. Durable ingest duy
  trì projection giảm gọn theo subject và asset; finalizer đọc projection typed theo operation/lane/page.
- Gate calibration: 2.500 subjects không chậm hơn median V19 `2.032 s` qua ba measured run cùng topology.
- Recovery qualification: `100.000 subjects × 10 assets` hoàn tất D2 dưới `60.000 ms` ở cả ba measured run;
  báo cáo median/max, không chỉ run tốt nhất. Nếu chi phí được chuyển sang ingest/reduction thì đồng hồ kết hợp
  D1 + D2 cũng phải dưới `60.000 ms`.
- Không có temp-file exhaustion, connection failure hay statement timeout; transaction p95 phải nhỏ hơn 1/3
  lease budget. Page size chỉ được chọn từ ladder `500 → 1.000 → 2.000` bằng evidence, không hard-code để né gate.
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

- Chưa có log chi tiết V21 nên chưa được khẳng định phase nào chiếm ưu thế. Bước đầu của V22 bắt buộc khóa
  phase profile; nếu evidence bác bỏ scratch/JSONB churn là bottleneck, dừng trước schema change và sửa Plan.
- Duy trì reduction trong D1 có thể làm ingest chậm. Vì vậy recovery gate kết hợp D1 + D2 ngăn việc chỉ dời
  chi phí ra ngoài `mergeMs`.
- Projection reduction phải là durable, idempotent và có đường rebuild từ `catalog_discovery_stage`; không dùng
  UNLOGGED làm source of truth sau crash.
- Profile 1M cold gần như toàn subject mới — bypass hash giúp D2; profile update/existing vẫn trả giá
  `catalog_subject_state_json` và phải có test parity, không lấy cold làm chứng minh mọi workload.
