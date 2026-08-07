# 027 Scan run SSE progress — Plan

Status: DONE — chờ verification runtime được người dùng cho phép.
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`, `gateway-service`; companion FE FT-004.
- Scope/files: Scan stream DTO/hub/service/SSE adapter/config; progress/terminal
  publish points; Gateway streaming route/config; OpenAPI/Gateway contract,
  service contexts và `docs/STATUS.md`.
- Must preserve: PostgreSQL source of truth, FT-026 fencing/terminal guarantee,
  bounded COPY/memory, REST proposal/issue pagination, stale-run `404`, Gateway
  correlation ID, no absolute-path exposure và client polling compatibility.
- Read on demand: FT-025/FT-026, `ScanChunkCommitter`, `ScanExecutor`, deadline/failure
  handlers, Spring Framework 7 `SseEmitter`, Gateway MVC streaming behavior,
  FE `004-scan-run-sse-progress` Design/Plan.

## Bước triển khai

1. Tạo versioned `ScanRunStreamEvent`, phase/type và framework-neutral signal hub có
   coalescing, capacity, initializing queue và idempotent subscription cleanup.
2. Tạo application stream service + SSE web adapter: validate run, race-free snapshot,
   named frames, shared heartbeat, max lifetime, callback cleanup và graceful shutdown.
3. Publish transient discovery progress tối đa 1 Hz và durable checkpoint/terminal
   sau commit từ mọi completion/failure/deadline path; SSE failure không ảnh hưởng run.
4. Cấu hình giới hạn/lifetime/heartbeat; thêm metrics/log không high-cardinality.
5. Chứng minh Gateway pass-through frame-by-frame và correlation header; chỉ thêm
   route-specific streaming adjustment nếu evidence cho thấy timeout/buffering hiện tại cần.
6. Triển khai FE FT-004: stream lifecycle, REST recovery, fallback polling và result
   refresh throttle; sau đó cập nhật context/status owner theo implementation thật.
7. Review contract producer/consumer cùng nhau bằng `$cross-service-contract`, rồi
   review async lifecycle bằng `$architecture-quality-review`.

## Kiểm tra

- Chưa chạy test/build/service theo yêu cầu người dùng.
- Khi được phép triển khai/verify: unit test coalescing/capacity/cleanup/race; MVC test
  event name/payload/404/429/terminal; Gateway integration test nhận frame trước khi
  stream complete và sống qua >30 giây với heartbeat.
- E2E FE–Gateway–Scan: start, progress, terminal, reconnect, hidden/visible, truncate
  `404`, SSE unavailable fallback và không refresh proposal khi user đang thao tác.
- Static gate hiện tại: OpenAPI/link/Mermaid/line cap và `git diff --check`.

## Rollout và rollback

- Rollout BE/Gateway trước; polling FE cũ vẫn chạy. Sau đó rollout FE dùng SSE.
- Có thể disable SSE FE để quay về polling mà không rollback DB/BE; endpoint additive.
- Rollback BE/Gateway bằng revert code/config; không có schema/data migration.
- Chỉ support một Scan Service instance trong feature này; trước scale ngang phải mở
  feature fan-out liên instance và review lại delivery/connection routing.

## Tài liệu cần cập nhật

- Đã cập nhật OpenAPI, Gateway HTTP contract, `docs/STATUS.md`,
  `apps/scan-service/CONTEXT.md` và `apps/gateway-service/CONTEXT.md`.
- FE companion FT-004 cập nhật `scan/CONTEXT_SCAN.md` thành lifecycle hiện hành.
