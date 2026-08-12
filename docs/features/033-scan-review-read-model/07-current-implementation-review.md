# FT-033 — Review triển khai hiện tại

## Cách flow đang chạy

`finalizeRun` ghi completion và enqueue một projection task trong cùng transaction. Worker claim task bằng `SKIP LOCKED`, rebuild snapshot proposal/issue theo generation bằng SQL set-based, rồi conditional swap pointer. API đọc generation `READY`; nếu chưa sẵn sàng thì fallback về historical query. Write authority vẫn là `scan_proposal`, `scan_issue`, `scan_decision` và `scan_outbox_event`; projection chỉ là bản sao có thể xoá và rebuild.

Các owner chính: `ScanRunProgressWriter`, `ScanReviewProjectionTaskStore`, `ScanReviewProjectionWorker`, `ScanReviewProjectionWriter`, `ScanReviewProjectionQueryService`, `ScanReviewDecisionProjection`.

## Invariant và failure

- Một root chỉ có một visible generation; generation cũ không được swap sau generation mới.
- Worker chết giữa lúc build thì transaction rollback; task được reclaim sau lease và quá retry/deadline thành `FAILED`.
- Decision khóa root watermark và merge lại decision trước swap để tránh lost update.
- Projection chưa `READY` không được trả dữ liệu stale như dữ liệu chuẩn; fallback phải giữ ordering/count.

## Điều chưa được chứng minh

Code và migration đã có, nhưng crash/reclaim, race projector–decision, Flyway/Testcontainers, deep pagination và benchmark 1M dưới tải projector chưa chạy. Vì vậy FT-033 là `IMPLEMENTED — verification deferred`, không phải capacity evidence. Liên quan `TD-017`, `TD-022` và gate FT-033 trong `docs/STATUS.md`.
