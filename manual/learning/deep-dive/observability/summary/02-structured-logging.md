# Structured Logging — Core Recall Sheet

> Nguồn giải thích và evidence: [Structured Logging & ELK deep-dive](../02-structured-logging-elk.md)

## North star

**Logging biến runtime event thành record có schema + context để tìm kiếm; pipeline tốt phải hữu ích khi failure xảy ra mà không kéo business flow hỏng theo.**

## Keyword spine

`Log event → Context → Schema → Encode → Append → Ship → Index → Query → Retain`

- **Event**: điều gì vừa xảy ra.
- **Context**: event thuộc request/job/entity nào.
- **Schema**: field có tên và ý nghĩa ổn định.
- **Encode/Append**: backend biến event thành bytes và ghi vào sink.
- **Ship/Index**: collector chuyển và storage lập chỉ mục.
- **Query/Retain**: điều tra được và giữ đúng thời gian cần thiết.

## Mental model một dòng

```text
Java → SLF4J → Logback → Spring Boot ECS → local *.json.log
                                            ↓
Kibana ← Elasticsearch data stream ← Logstash + sincedb
```

## 9 core truths

1. **SLF4J là facade; Logback là backend** — API gọi log khác engine thực thi appender.
2. **ECS là schema/format, không phải shipper** — bật ECS không bỏ Logback và không bắt buộc Logstash.
3. **Structured > JSON thuần** — giá trị nằm ở field ổn định để query, không chỉ dấu `{}`.
4. **MDC mang context, không giữ business state** — set/propagate/cleanup theo request scope.
5. **File shipping tách app khỏi network collector** — nhưng disk chậm/full vẫn có thể ảnh hưởng app.
6. **Project hiện chưa cấu hình `AsyncAppender`** — không được nói caller chỉ enqueue hoặc “logging luôn non-blocking”.
7. **Logback `AsyncAppender` dùng `BlockingQueue`, không phải RingBuffer** — queue có trade-off drop/block/heap/flush.
8. **`sincedb` là checkpoint, không phải exactly-once** — crash/rotation/checkpoint có thể tạo loss hoặc duplicate.
9. **Tách logs data stream và media index là logical isolation** — cùng Elasticsearch instance vẫn tranh CPU/heap/disk.

## Default, optional và project-configured

| Loại | Điều cần nhớ |
| --- | --- |
| Framework default | Spring Boot dùng Logback khi starter logging hiện diện; Logback async không tự xuất hiện chỉ vì ghi file. |
| Optional | Có thể cấu hình `AsyncAppender`, rolling policy, shipper khác, lifecycle/ILM và redaction. |
| Backend V2 | Spring Boot `4.0.3`; file ECS `*.json.log`; Logstash tail + `sincedb`; Elasticsearch logs data stream; Kibana Discover. |
| Chưa được chốt | Explicit rolling limits, retention Elasticsearch production, async appender và exactly-once delivery. |

## Decision rules

- Nếu cần query theo ID/status/service, **ghi thành field**; đừng nhét mọi thứ vào `message`.
- Nếu caller latency do appender đáng kể, **đo rồi cân nhắc async**; chốt rõ drop hay block khi queue đầy.
- Nếu shipper có thể down lâu, **retention file phải lớn hơn worst-case backlog**.
- Nếu log có secret/path nhạy cảm, **redact trước sink**; đừng trông chờ xóa ở Kibana.
- Nếu cần audit durable/exactly-once, **đừng dùng operational logging như source of truth**.
- Nếu logs và business search cùng cluster, **tách index/policy chưa đủ**; vẫn phải quản trị tài nguyên cluster.

## Failure compass

| Hỏng ở đâu | Nghĩ ngay đến |
| --- | --- |
| Appender/file | caller latency, disk full, rotation |
| Logstash | backlog, retry, checkpoint |
| `sincedb` | resume position, re-read/loss edge case |
| Elasticsearch | indexing backpressure, mapping, disk/heap |
| Schema/context | query không ra, cardinality, data leak |

## Trả lời phỏng vấn 30 giây

> Backend V2 tạo log qua SLF4J/Logback, Spring Boot 4 encode file theo ECS, Logstash tail file với `sincedb`, Elasticsearch index logs data stream và Kibana query theo field. Điểm Senior cần nói là ranh giới và failure semantics: project chưa cấu hình async, checkpoint không phải exactly-once, và file shipping chỉ giảm network coupling chứ không xóa rủi ro disk/backlog.

## Answer spine 2 phút

1. Bắt đầu từ **log event = timestamp + severity + source + message + context + outcome**.
2. Tách component: **facade → backend → encoder → appender → shipper → storage → UI**.
3. Giải thích project: **ECS file → Logstash tail → data stream → KQL**.
4. Nêu guarantee trung thực: **best effort + checkpoint**, có thể loss/duplicate.
5. Kết bằng trade-off: sync/async, disk/backlog, retention, security và shared-cluster contention.

## Red flags

- “JSON tự động là structured logging tốt.”
- “ECS thay thế Logback hoặc Logstash.”
- “Ghi file qua OS cache nghĩa là async.”
- “Logback async dùng RingBuffer và không bao giờ block.”
- “`sincedb` đảm bảo không mất/không trùng.”
- “Logstash down thì app chắc chắn không bị ảnh hưởng.”
- “Khác index nghĩa là khác tài nguyên vật lý.”
- Dùng số latency/retention/tỷ lệ nén không có evidence.

## Active recall — che phần trên trước khi trả lời

1. Một log event tối thiểu cần những nhóm field nào?
2. SLF4J, Logback và ECS khác nhau ở đâu?
3. Vì sao JSON chưa chắc là structured logging tốt?
4. Project có bằng chứng nào cho thấy logging đang async không?
5. Khi `AsyncAppender` queue đầy, hai chiến lược chính là gì?
6. `sincedb` nhớ gì và không đảm bảo điều gì?
7. Ba lớp rotation/retention khác nhau ra sao?
8. Vì sao tách index chưa tạo resource isolation?
9. Khi nào operational log không đủ để làm audit trail?

Luyện sâu hơn tại [Logging question bank](../question-bank/02-logging-questions.md).
