# UC-01 — Scan → Catalog canonical ingestion

## Mục tiêu phỏng vấn

Giải thích và bảo vệ được vì sao một hệ thống quản lý media tách **discovery/review** khỏi **canonical write model**, nhưng vẫn không bỏ mất quyết định approve khi Kafka hoặc consumer gặp lỗi.

## Scenario

1. Admin tạo scan preview từ một `rootKey` đã cấu hình; Scan chỉ đọc filesystem và tạo proposal/issue trong `scan_db`.
2. Admin approve proposal hợp lệ. Scan ghi scan item/decision và outbox trong cùng local transaction.
3. Relay phát `media.file.discovered.v1` với cùng `eventId` khi retry.
4. Catalog consume at-least-once, dedupe `eventId`, upsert canonical Subject/Asset và không đọc filesystem của Scan.

```text
rootKey → Scan preview/proposal → admin approval + scan outbox
        → Kafka media.file.discovered.v1 → Catalog dedupe + canonical upsert
```

## Boundary cần nói chính xác

| Thành phần | Sở hữu | Không sở hữu |
| --- | --- | --- |
| Scan | `scan_db`, scan run/proposal/issue/item/outbox và parser theo root | Subject/Asset canonical, Query projection, thay đổi filesystem |
| Kafka | Delivery at-least-once và thứ tự theo partition key | Transaction ACID xuyên Scan/Catalog, deduplication nghiệp vụ |
| Catalog | `catalog_db`, processed event, canonical Subject/Asset và Catalog outbox | Filesystem scan root, Query projection |

Nguồn kiểm chứng: [Scan context](../../../../apps/scan-service/CONTEXT.md), [Catalog context](../../../../apps/catalog-service/CONTEXT.md), [event contract](../../../../docs/contracts/events/media.file.discovered.v1.md).

## Failure drills tối thiểu

| Tình huống | Kỳ vọng cần giải thích |
| --- | --- |
| Scan crash sau local commit, trước Kafka acknowledgement | Outbox chưa `published_at` được relay retry; approval local không mất. |
| Cùng record Kafka được giao nhiều lần | Catalog dedupe `eventId` trong transaction với canonical upsert; không tạo asset trùng. |
| Catalog unavailable / consumer lỗi tạm thời | Kafka lag hoặc retry tăng; không rollback Scan approval. |
| Payload không phục hồi được | Retry hữu hạn rồi DLT giữ record gốc và error headers; không crash loop consumer. |
| Proposal mơ hồ | Tạo issue/review, không tự đoán và không phát canonical mutation. |

## Lộ trình artifact

| Bước | Owner | Việc cần chốt | Trạng thái |
| --- | --- | --- | --- |
| 1 | Deep-dive | Audit [Scan](../../deep-dive/scan-service/00-overview.md) và [Outbox](../../deep-dive/transactional-outbox/README.md) theo event contract/code hiện tại; sửa fact lệch trước | Tiếp theo |
| 2 | Summary | Một recall sheet xuyên Scan → Catalog: transaction boundary, outbox, delivery, dedupe, DLT | Chưa tạo |
| 3 | Question bank | Chain FOUNDATION → SENIOR → ARCHITECT và anchor về dual-write, idempotency, partition key, replay | Chưa tạo |
| 4 | Evidence | Diễn đạt/làm được test crash, duplicate, retry/DLT và kiểm tra canonical result | Chưa chốt |

Không dùng event version, port, command vận hành hoặc class name từ tài liệu cũ làm evidence nếu chưa đối chiếu với contract/code. Đặc biệt contract hiện hành là `media.file.discovered.v1`; correlation và W3C `traceparent` truyền bằng Kafka headers, không nằm trong JSON payload.

## Tiêu chí chuyển UC-02

- Nói được answer 2 phút cho scenario mà không nhầm ownership hoặc hứa “exactly once”.
- Có deep-dive đã audit, summary, question bank và ít nhất bốn failure drill ở trên.
- Phân biệt được outbox guarantee, Kafka delivery và Catalog idempotency; biết đường link contract/code để chứng minh.
- Không mở rộng sang Query/Elasticsearch trước khi canonical ingestion đã rõ.
