# Trạng thái Backend V2

Updated: 2026-08-08

## Trọng tâm hiện tại — FT-033

[FT-033 — Scan review read model](./features/033-scan-review-read-model/01-brief.md) đang ở
`DRAFT` và **NOT READY** sau architecture review.

Mục tiêu là tách review read path khỏi các query anti-join lịch sử, đồng thời không làm regress scan
hot path 1M file. Ranh giới đang theo đuổi:

- Scan chunk vẫn giữ direct PostgreSQL `COPY`, set-based inventory, `REQUIRES_NEW`, lease fence và
  deadline.
- Terminal scan chỉ được phép tạo một durable projection handoff O(1) trong cùng transaction finalize;
  không ghi projection item theo từng proposal/issue trong chunk.
- Projector chạy bất đồng bộ, batch bounded, idempotent, có ordering/fence theo `root` và có resource
  budget riêng; read API chỉ đọc projection sau cutover.
- Decision authority vẫn nằm ở write model; read-after-commit chỉ là ngoại lệ nhỏ, phải có merge rule
  để projector cũ không ghi đè decision mới.

## Gate bắt buộc trước khi code FT-033

1. Chọn durable delta hay async root rebuild; không tuyên bố đồng thời không thêm write, không rebuild
   nặng và vẫn incremental chính xác.
2. Chốt atomic handoff, root generation/conditional mutation và merge rule giữa projector với decision.
3. Chốt task lease, timeout, retry, stale reclaim, restart/shutdown và terminal failure state.
4. Chốt freshness contract: watermark/status, dữ liệu stale khi lag và semantics cho queue toàn cục.
5. Chốt rollout không phá global ordering/pagination; sau đó benchmark scan 1M khi projector có tải.

Evidence nền tảng và findings nằm ở [mental model](./features/033-scan-review-read-model/04-read-write-separation-mindset.md)
và [architecture review](./features/033-scan-review-read-model/05-architecture-review.md). Không sửa
`01-brief.md`, `02-design.md`, `03-plan.md` cho tới khi các gate trên được hiểu và quyết định rõ.

## Trạng thái đã ổn định

- SC-01 Scan API và persistence hot path của [FT-028](./features/028-parallel-reconciliation-pipeline/03-plan.md)
  đã có parallel analyze, direct `COPY`, set-based reconciliation và checkpoint lease-fenced.
- [FT-030 telemetry](./features/030-scan-performance-telemetry/03-plan.md) đã có runtime evidence cho
  terminal timeline và chunk persistence theo `runId`.
- [FT-031 persistence optimization](./features/031-scan-reconciliation-persistence-optimization/03-plan.md)
  đã benchmark run 1M file dưới 30 giây; không tuning lại chunk size nếu chưa có hypothesis/evidence mới.
- [FT-032 Scan review queue](./features/032-scan-review-queue/03-plan.md) có code tối thiểu ở trạng thái
  `DONE`, nhưng còn chờ architecture review; FT-033 là hướng xử lý nền tảng cho read path tiếp theo.
- [FT-013 Media Worker processing foundation](./features/013-media-worker-processing-foundation/03-plan.md)
  vẫn `READY`, nhưng không phải trọng tâm của session hiện tại.

## Deferred và gate rộng hơn

- Verification deferred: FT-025 semantics Testcontainers, FT-026 timeout/lease-loss, FT-027 E2E
  Gateway/SSE; thực hiện theo Plan owner khi có scope hardening phù hợp.
- Phase 4 còn thiếu Media Worker processing pipeline; Phase 7 còn thiếu importer/backfill V1.
- Observability mở rộng còn thiếu alert/SLO, profiling sâu và k6.

## Nợ kỹ thuật đang mở

Xem [TECHNICAL_DEBT.md](./TECHNICAL_DEBT.md) — hiện còn TD-004, TD-005 và TD-006. STATUS chỉ giữ
liên kết snapshot; chi tiết remediation nằm ở debt/feature owner.

## Việc tiếp theo theo thứ tự ưu tiên

1. Đọc và chốt mental model/trade-off của FT-033; quyết định durable delta hoặc root rebuild.
2. Cập nhật lại FT-033 Design/Plan theo các gate và findings; chưa triển khai code.
3. Sau khi Design đạt READY, mới implement projector/handoff và benchmark cạnh tranh tài nguyên với
   scan 1M file.
