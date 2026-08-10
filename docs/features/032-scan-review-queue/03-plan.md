# FT-032 — Kế hoạch Scan review queue

Status: DONE — code tối thiểu đã hoàn tất, chờ review architecture.
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service` / `scan_db`; consumer FE là module `scan` ở
  `D:\Pesonal\file-management\v2\file-mngt-fe-v2` qua Gateway.
- Scope/files: Scan OpenAPI, controller/query/decision/repository/DTO/test, có thể
  thêm Flyway index sau khi có evidence; FE queue page/API client/state/test theo
  `D:\Pesonal\file-management\v2\file-mngt-fe-v2\docs\features\005-scan-review-queue`;
  `docs/STATUS.md` khi implementation hoàn tất.
- Must preserve: incremental reconciliation hot path, `RUNNING` SSE policy,
  proposal ownership, immutable `APPROVE`, transaction decision + outbox, không
  Kafka event khi `REJECT → PENDING`, no cross-database access, source dưới 500
  dòng/file.
- Read on demand: [Design](./02-design.md), `apps/scan-service/CONTEXT.md`,
  `docs/contracts/openapi/scan-v1.yaml`, `docs/contracts/http/gateway-routing-v1.md`,
  `docs/architecture/03-CODING_RULES.md` và context repo FE trước khi code FE.

## Bước triển khai BE

1. Giữ OpenAPI trong commit đầu tiên: thêm queue/reopen schema, parameters, error
   responses và ví dụ; không đổi endpoint scan/decision hiện có.
2. Bổ sung repository query phân trang cho `PENDING`/`REJECTED`, optional `rootKey`
   và chỉ run `COMPLETED`; tránh N+1 khi map decision/run vào response.
3. Thêm read use case/DTO/controller cho queue. Validate `state`, `rootKey`, page và
   size tại HTTP boundary; response không trả absolute filesystem path.
4. Thêm use case reopen transaction: verify run/proposal, no-op `PENDING`, delete
   chỉ `REJECT`, conflict `APPROVE`; không gọi outbox/publisher/Catalog.
5. Chỉ sau `EXPLAIN (ANALYZE, BUFFERS)` representative mới quyết định migration
   index. Không thêm index vào COPY hot path chỉ theo suy đoán.

## Bước triển khai FE

1. Đọc `D:\Pesonal\file-management\v2\file-mngt-fe-v2\AGENTS.md` và
   `scan/CONTEXT_SCAN.md` trước khi code; FE V1 không thuộc scope trừ khi người
   dùng yêu cầu rõ.
2. Tạo tab `Chờ duyệt`, paging và filter `rootKey`; dùng `state=PENDING` mặc định.
3. Tạo filter `Đã bỏ qua` với action reopen; sau response `204`, refetch queue.
4. Giữ SSE/REST terminal behavior FT-028: sidebar là entry point duy nhất vào
   queue; không poll proposal/issues khi `RUNNING`.
5. Map lỗi `409` reopen approved thành thông báo rõ ràng; không tự gọi API decision
   trái ngược để “undo” approval.

## Kiểm tra khi người dùng cho phép

- Unit: state mapping, validation và reopen `PENDING`/`REJECTED`/`APPROVED`.
- Integration Testcontainers: queue xuyên nhiều run/root, pagination order/filter,
  proposal RUNNING bị loại, reopen không tạo outbox/Kafka event và concurrent
  reopen idempotent.
- Query evidence: `EXPLAIN (ANALYZE, BUFFERS)` với dữ liệu representative, lưu kết
  quả trong Plan nếu phải thêm index.
- FE: test API client, filter/paging, transition reject → reopen → pending, banner
  diff=0 và không refetch list trong `RUNNING`.
- E2E: scan lần một tạo proposal, người dùng reject, scan lại không đổi, mở Đã bỏ
  qua, reopen và approve; xác minh chỉ approval tạo một outbox discovery event.

## Rollout và rollback

- Rollout additive: API cũ không đổi; FE mới chỉ dùng endpoint mới khi đã deploy
  Scan version chứa feature.
- Rollback FE chỉ ẩn queue/reopen UI. Rollback BE giữ nguyên decision rows; không
  xóa proposal, decision hay outbox. Nếu có migration index, không cần rollback
  data để tắt UI/API.

## Source-of-truth audit

- Cập nhật `docs/contracts/openapi/scan-v1.yaml` trong task thiết kế này vì REST
  contract thay đổi additive.
- Không cần ADR, Kafka contract, architecture summary hoặc service context vì
  ownership/boundary không đổi.

## Kết quả triển khai

- Đã thêm queue `PENDING`/`REJECTED` xuyên các run `COMPLETED` và API reopen
  `REJECT → PENDING`; `APPROVE` trả conflict và không phát outbox/event mới.
- Đã thêm history issue phân trang xuyên các run `COMPLETED`; history không gán
  trạng thái đã xử lý và không tạo write/event mới.
- Đã cập nhật Scan OpenAPI. Không thêm migration/index vì chưa có evidence `EXPLAIN`.
- Không chạy build, test, migration hay service theo yêu cầu của người dùng. Các mục
  verification phía trên được giữ để thực hiện sau review architecture.
