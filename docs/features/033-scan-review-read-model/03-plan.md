# FT-033 — Kế hoạch Scan review read model

Status: DRAFT
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service` / `scan_db`; FE V2 chỉ tiêu thụ contract hiện có sau cutover.
- Scope/files: Flyway read-model tables/indexes; projection task/repository/worker;
  query repository/DTO/controller; decision service transaction; Scan terminal handoff;
  OpenAPI additive field nếu trạng thái đồng bộ được expose.
- Must preserve: direct `COPY` proposal/issue, set-based inventory reconciliation,
  chunk `REQUIRES_NEW`, lease fence/deadline, `APPROVE` outbox transaction,
  `REJECT → PENDING` không tạo event, database ownership và source dưới 500 dòng/file.
- Read on demand: FT-028/031, FT-032, `apps/scan-service/CONTEXT.md`,
  `docs/contracts/openapi/scan-v1.yaml`, `docs/architecture/03-CODING_RULES.md`, TD-006.

## Gate trước khi code

1. Chốt durable delta source cho projector: bảng delta tối thiểu hay root rebuild set-based;
   không được dùng `UNLOGGED scan_inventory_diff_stage` sau terminal làm replay source.
2. Chốt SLA projection lag và semantic API khi watermark chưa bắt kịp.
3. Chốt strategy worker: polling task nội bộ hay outbox/Kafka nội bộ. Nếu dùng event/outbox,
   cập nhật event contract trước code và xác định dedupe/retry/DLT.

## Bước triển khai

1. Tạo migration projection item/summary/task và các index read-only; có cleanup/rebuild
   strategy, unique constraint, checkpoint/version fence.
2. Tạo application port/use case tạo task terminal, projector batch transaction và reaper;
   đo lường liveness, retry, idempotency, root rebuild.
3. Đưa decision write + projection update vào cùng transaction; giữ outbox approval atomic.
4. Chuyển queue/issues/counter query sang projection; giữ fallback hoặc feature flag trong
   rollout để so sánh với query lịch sử.
5. Cập nhật OpenAPI/source-of-truth nếu expose watermark; chỉ sau đó FE đổi behavior hiển thị.
6. Benchmark 1M file và load queue lớn; sau evidence quyết định bỏ/giữ V14.

## Kiểm tra

- Unit: task dedupe, state transition projection, stale task reclaim, decision read-after-write.
- Testcontainers: scan mới/changed/missing, issue chuyển proposal, concurrent decision với
  projector, crash/retry giữa batch, rebuild root và API pagination stable.
- Query evidence: `EXPLAIN (ANALYZE, BUFFERS)` cho Pending/Rejected/Approved/Issue trên 1M
  fixture; so sánh với FT-032 query lịch sử, lưu latency và buffer evidence trong Plan.
- Runtime: scan hot-path benchmark FT-031 không regress; projection lag/backlog có metric và
  terminal scan không chờ batch projector.

## Rollout và rollback

- Rollout additive: backfill/read projection theo root, đối chiếu count với query cũ rồi bật
  read flag từng root. Write model và endpoint shape giữ nguyên.
- Rollback chỉ chuyển read flag về query cũ; task/projection có thể tiếp tục hoặc dừng an toàn.
  Không xóa write model, decision, outbox hay inventory. Không drop projection data cho đến khi
  có evidence rollback đã ổn định.

## Tài liệu cần cập nhật

- Bắt buộc: `docs/contracts/openapi/scan-v1.yaml` nếu có field/API trạng thái projection,
  `apps/scan-service/CONTEXT.md` khi worker/bảng mới đã thành owner fact.
- Có điều kiện: event contract/ADR nếu chọn outbox/Kafka hoặc physical read database.
- Không cập nhật FE docs trong feature này; FE chỉ vào scope sau khi contract đã READY.
