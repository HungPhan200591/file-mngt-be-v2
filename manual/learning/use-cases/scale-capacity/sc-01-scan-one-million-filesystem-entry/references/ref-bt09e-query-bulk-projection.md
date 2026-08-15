# Reference Capsule: BT-09E — Query Bulk Projection & Cache Generation

> Trích xuất từ: `docs/reviews/2026-08-13-approve-5000-query-performance-assessment.md` (Section 4, 5, 6 & P0/P1) & `2026-08-14-linkedin-large-scale-data-processing.md` (Section 4).
> Phạm vi: Áp dụng cho Query Service khi cập nhật Read Model từ event `media.subject.changed.v2`.

---

## 1. Vấn đề của Query Service cũ

- **Per-record Projection Transaction**: Mỗi event mở 1 transaction, xóa sạch và chèn lại các child collection (`assets`, `tags`, `preview_items`), tạo hàng ngàn câu lệnh DELETE/INSERT rời rạc.
- **Redis Invalidation đơn lẻ**: Gửi từng lệnh `DEL` qua Redis network round-trip cho từng subject, làm chậm luồng commit DB.
- **Elasticsearch publisher đồng bộ**: Giữ transaction database trong khi gọi HTTP sang Elasticsearch cluster.

---

## 2. Thiết kế Bulk Projection & Cache Generation

```mermaid
flowchart TB
    KAFKA{{"Kafka: media.subject.changed.v2"}} --> BATCH["Batch consumer"]
    BATCH --> DEDUP["Deduplicate theo operationId + subjectId"]
    DEDUP --> SET_SQL["Bulk COPY hoặc set-based upsert"]
    SET_SQL --> DB_COMMIT[("Commit query_db projection")]
    DB_COMMIT --> CACHE_SWITCH(("Switch cacheGeneration O(1)"))
    CACHE_SWITCH --> READY(["QUERY_DB_READY"])
    DB_COMMIT -.-> SEARCH["Async search bulk worker"]
    SEARCH -.-> SEARCH_READY(["SEARCH_READY"])
```

---

## 3. Các quy tắc kỹ thuật bắt buộc

1. **Deduplicate trong Batch**: Nếu 1 batch có nhiều event cho cùng một `(operationId, subjectId)`, chỉ giữ event có `subjectVersion` cao nhất.
2. **Bulk Upsert Read Model**:
   - Sử dụng set-based SQL `INSERT ... ON CONFLICT (subject_id) DO UPDATE` với điều kiện `WHERE EXCLUDED.subject_version > query_subject.projection_version`.
   - Chặn đứng hoàn toàn rủi ro out-of-order event ghi đè dữ liệu mới bằng dữ liệu cũ.
3. **Cache Generation Switch**:
   - Tạo `cacheGeneration` mới cùng durable operation state sau khi projection commit.
   - Redis chỉ là cache; lỗi Redis phải bypass/fallback Query DB và không được chặn `QUERY_DB_READY`.
4. **Tách hoàn toàn Elasticsearch khỏi Critical Path**:
   - Ghi search outbox table hoặc publish search event nội bộ.
   - Worker riêng biệt dùng Elasticsearch `_bulk` API (ví dụ: gom 1.000 items/request) để cập nhật search index, không làm chậm `QUERY_DB_READY`.
