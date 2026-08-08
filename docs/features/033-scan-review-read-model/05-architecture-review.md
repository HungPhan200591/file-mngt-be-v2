# FT-033 — Architecture Review Notes

Ngày review: 2026-08-08
Phạm vi: `01-brief.md`, `02-design.md`, `03-plan.md` và dependency trực tiếp của Scan review queue.
Loại tài liệu: review note phục vụ thảo luận; không thay thế Design/Plan và chưa chốt implementation.

## Kết luận

**Verdict: NOT READY**

Hướng projection cùng `scan-service` là hợp lý để loại anti-join lịch sử khỏi GET request path và
không thêm index/write vào từng chunk `COPY`. Tuy nhiên thiết kế chưa khóa atomic handoff, ordering
theo root, race với decision, durable delta và semantics khi projection lag.

Mục tiêu boundary nên được diễn đạt chính xác:

> Không chạm scan chunk hot path. Chỉ cho phép một handoff O(1) trong transaction finalize; mọi
> projection batch nằm ngoài transaction scan và có resource budget riêng.

“Không đụng write path hoàn toàn” không tương thích với yêu cầu không mất task nếu không có một cơ
chế CDC/durable handoff tương đương.

## Readiness

| Tiêu chí | Trạng thái | Evidence |
| --- | --- | --- |
| GET queue không anti-join history sau cutover | PASS về hướng thiết kế | `02-design.md` — Read API chỉ đọc projection |
| Không thêm projection write/index vào chunk COPY | PASS | `01-brief.md` — chỉ tạo yêu cầu sau terminal |
| Atomic handoff | MISSING | Brief và Design mô tả khác nhau về thời điểm tạo task |
| Durable create/update/delete delta | MISSING | `scan_inventory_diff_stage` không durable và bị dọn |
| Idempotency | PARTIAL | Có unique/task retry nhưng chưa có generation fence |
| Ordering giữa nhiều run cùng root | MISSING | Chưa định nghĩa root sequence hoặc conditional mutation |
| Decision read-after-commit | PARTIAL | Có synchronous projection update nhưng chưa có merge rule |
| Worker liveness | PARTIAL | Có lease/reaper ở mức ý tưởng, chưa có deadline/shutdown policy |
| Resource isolation với scan | MISSING | Cùng DB, chưa có pool/concurrency/timeout/pause policy |
| Projection freshness contract | MISSING | Chưa chốt status, watermark và stale response semantics |
| Rollout global pagination | MISSING | Feature flag theo root xung đột queue không bắt buộc root |

## Findings

### [High] Terminal handoff có crash gap

**Location:** `01-brief.md` mục acceptance criteria; `02-design.md` mục idempotency.
**Condition:** task được tạo sau khi terminal transaction đã commit.
**Impact:** process chết giữa hai commit làm run `COMPLETED` không bao giờ được project.
**Recommendation:** `markMissing + completeRun(fenced) + INSERT task UNIQUE(scan_run_id)` trong
cùng transaction `finalizeRun`. Transaction này không được dựng projection item.

### [High] Thiếu ordering/fencing theo root

**Location:** `02-design.md` mô tả lease task và upsert theo unique root/path.
**Condition:** projector của run cũ chậm hơn và commit sau projector của run mới.
**Impact:** observation cũ khôi phục item đã mất, xóa item mới hoặc làm sai summary.
**Recommendation:** cấp `root_generation` tăng đơn điệu; mọi upsert/delete phải conditional theo
generation. Serialize mutation theo root hoặc dùng root-level lease/fence.

### [High] Race giữa projector và decision chưa có merge rule

**Location:** `02-design.md` yêu cầu decision cập nhật projection đồng bộ.
**Condition:** user quyết định một proposal trong lúc projector lag đang xử lý task cũ.
**Impact:** projector có thể ghi đè state vừa approve/reject/reopen; counter bị lost update.
**Recommendation:** projector không ghi đè decision fields, hoặc luôn merge authority decision mới
nhất, kèm conditional version. Summary phải cập nhật set-based/recompute có fence.

### [High] Bulk action vẫn đọc lịch sử write model

**Location:** `ScanDecisionService.decideReviewQueue` và `reopenReviewQueue`; `03-plan.md` chỉ chuyển
queue/issues/counter query.
**Condition:** POST bulk approve/reject/reopen chọn candidate bằng query anti-join hiện tại.
**Impact:** GET nhanh nhưng bulk action vẫn cạnh tranh với scan và tải candidate không bounded vào
memory/transaction.
**Recommendation:** chọn candidate ID từ read model; conditional-write authority theo batch bounded
hoặc set-based; giữ decision + approval outbox + projection mutation atomic.

### [High] Durable delta chưa được chọn

**Location:** `01-brief.md` câu hỏi mở; `03-plan.md` gate trước code.
**Condition:** staging bị dọn sau terminal, trong khi missing/delete cần được replay.
**Impact:** projector không biết chính xác path nào cần xóa, hoặc phải rebuild/query toàn root.
**Recommendation:** chốt một trong hai hướng trước schema:

- Durable delta: trả thêm write amplification để projection incremental/replay chính xác.
- Root rebuild async: giữ write path tối giản nhưng phải throttle, fence và đo DB contention.

Không được tuyên bố đồng thời “không delta write”, “không rebuild nặng” và “incremental chính xác”.

### [High] Chưa có liveness policy hoàn chỉnh cho worker mới

**Location:** `02-design.md` và `03-plan.md` mới nêu lease, checkpoint, reaper.
**Condition:** database call treo, task poison, service shutdown hoặc restart giữa batch.
**Impact:** task giữ `RUNNING` vô hạn, backlog không tự phục hồi hoặc worker cũ commit muộn.
**Recommendation:** chốt operation timeout, stale deadline, total task deadline, retry/backoff,
terminal failure state, root fence và graceful shutdown. Durable task phải được reclaim sau restart.

### [Medium] Rollout theo root không tương thích queue toàn cục

**Location:** `03-plan.md` rollout bật read flag từng root.
**Condition:** `GET /review-queue` không truyền `rootKey`.
**Impact:** trộn query cũ/mới phá global ordering, total count và pagination.
**Recommendation:** backfill toàn bộ trước global cutover, hoặc tạm bắt buộc rootKey, hoặc dùng
contract/version riêng trong rollout.

### [Medium] Freshness contract chưa đủ rõ

**Location:** `02-design.md` nói có thể thêm `projectionStatus` và `projectionUpdatedAt`.
**Condition:** projection đang lag, fail hoặc rebuild.
**Impact:** client không biết list/counter đại diện dữ liệu tới run nào; số liệu stale có thể bị hiểu
là đầy đủ.
**Recommendation:** định nghĩa state machine tối thiểu và watermark theo root; chốt API trả stale,
fallback hay lỗi cho từng state trước khi cập nhật OpenAPI.

### [Medium] Cùng database chưa bảo vệ tài nguyên cho writer

**Location:** toàn bộ HLD hiện đặt projector trong `scan_db`.
**Condition:** projector rebuild/batch lớn chạy đồng thời scan mới.
**Impact:** tranh connection, I/O, lock, WAL và autovacuum dù scan không chờ projector.
**Recommendation:** pool riêng, concurrency nhỏ, batch bounded, statement timeout, backlog limit và
policy pause/throttle theo root đang `RUNNING`; benchmark scan 1M khi projector có tải.

### [Low] Tham chiếu migration V14 không đúng trạng thái repository

**Location:** `02-design.md` gọi `V14__add_review_queue_read_indexes.sql` là biện pháp chuyển tiếp.
**Condition:** repository hiện chỉ có migration tới V13; FT-032 Plan ghi chưa thêm index vì thiếu
`EXPLAIN` evidence.
**Impact:** người triển khai có thể hiểu nhầm migration đã tồn tại.
**Recommendation:** khi sửa Design, ghi đây là đề xuất chưa tồn tại hoặc bỏ tham chiếu.

## Safety và liveness

### Safety còn thiếu

- Stale task không overwrite generation mới.
- Projector không overwrite decision mới.
- Summary không lost update.
- Rebuild không để reader nhìn dữ liệu nửa cũ nửa mới.

### Liveness còn thiếu

- Mọi task đi tới `COMPLETED` hoặc `FAILED`, kể cả worker chết và DB timeout.
- Stale task được reclaim sau restart.
- Poison task có retry budget và terminal state.
- Projector shutdown không nhận task mới và không tự ý release lease cho worker khác commit chồng.

## Boundary kiến trúc đề xuất để thảo luận

### Scan hot path

- Giữ direct `COPY`, set-based inventory, `REQUIRES_NEW`, lease fence và deadline hiện tại.
- Không thêm projection row/index mutation vào reconciliation chunk.
- Finalize chỉ thêm một task durable O(1) cùng terminal commit.

### Projection path

- Worker, transaction, timeout, connection budget và observability riêng.
- Serialize/fence theo root generation.
- Batch bounded, idempotent, checkpoint được và rebuild được.
- Không được làm terminal scan chờ projection hoàn thành.

### Review request path

- GET chỉ đọc projection sau cutover.
- Bulk candidate selection cũng đọc projection, nhưng authority write vẫn conditional trên write model.
- Response nói rõ freshness khi projection chưa READY.

## Các quyết định bắt buộc trước khi sửa Design

1. Chọn durable delta hay async root rebuild.
2. Chốt root generation và conditional mutation rule.
3. Chốt exact transaction boundary của terminal handoff.
4. Chốt merge rule giữa projector và decision.
5. Chốt worker timeout/retry/reaper/shutdown policy.
6. Chốt freshness contract và rollout cho queue global.
7. Đặt resource budget rồi benchmark scan 1M dưới projector load.

## Accepted risks / waivers

Không có. Chưa có rủi ro nào được người dùng chấp nhận làm waiver cho implementation.

## Tài liệu liên quan

- [Nền tảng tư duy tách Write Path và Read Path](./04-read-write-separation-mindset.md)
- [FT-033 Brief](./01-brief.md)
- [FT-033 Design](./02-design.md)
- [FT-033 Plan](./03-plan.md)
- [Scan Service context](../../../apps/scan-service/CONTEXT.md)
