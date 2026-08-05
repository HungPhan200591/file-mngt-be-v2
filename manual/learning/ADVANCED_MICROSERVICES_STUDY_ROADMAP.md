# Lộ trình study Microservices nâng cao — Senior Java

> Dự án thực hành: `file_mngt_microservice`  
> Mục tiêu: dùng các luồng nghiệp vụ thật của Backend V2 để luyện năng lực Senior Java: ownership, transaction boundary, event delivery, failure model, vận hành và trade-off.

## Cách dùng lộ trình

Đây là **roadmap điều phối**, không phải nơi sao chép lý thuyết hay source of truth kiến trúc. Đọc kiến trúc, contract và code thật trước; mỗi use case chỉ tạo study pack khi bắt đầu làm.

Một use case hoàn thành theo thứ tự:

```text
Scenario thật → Deep-dive (WHY → HOW → FAILURE → TRADE-OFF)
              → Summary ôn nhanh → Question bank → failure test / evidence
```

- Deep-dive sở hữu giải thích và bằng chứng; summary không tự thêm claim mới; question bank chỉ sinh từ hai artifact trước.
- Mọi trạng thái triển khai phải đối chiếu [`docs/STATUS.md`](../../docs/STATUS.md). Architecture, contract, context service và code mới là source of truth.
- Không biến roadmap thành backlog công nghệ. Một công nghệ chỉ được học khi nó giải quyết failure mode của use case hiện tại.

Xem quy ước thư mục, trạng thái và card use case tại [Use case study hub](./use-cases/README.md). Đọc [System Primer](./system-primer/README.md) trước khi bắt đầu use case đầu tiên.

## Lộ trình theo dependency thực tế

| Thứ tự | Use case | Dịch vụ trọng tâm | Năng lực Senior cần chứng minh | Trạng thái study |
| --- | --- | --- | --- | --- |
| 0 | Nắm domain, ownership và luồng dữ liệu | Toàn hệ thống | nói đúng Subject/Asset, database ownership, event boundary | Đọc primer trước |
| 1 | [UC-01 — Scan → Catalog canonical ingestion](./use-cases/01-scan-to-catalog-canonical-ingestion/README.md) | Scan, Catalog | async job, parser boundary, review, local transaction, outbox, idempotent consumer | Bắt đầu tại đây |
| 2 | UC-02 — Catalog → Query read projection | Catalog, Query | snapshot event, version guard, eventual consistency, replay/rebuild | Làm sau UC-01 |
| 3 | UC-03 — Query search, cache và reconciliation | Query | read-model design, Elasticsearch/PostgreSQL/Redis boundary, degraded read, đo đạc | Làm sau UC-02 |
| 4 | UC-04 — Media processing pipeline | Media Worker, Catalog, Query | work queue, safe filesystem I/O, bounded concurrency, completion event | Gắn với FT013 `READY` |
| 5 | UC-05 — Quan sát và chẩn đoán E2E | Gateway, Scan, Catalog, Query, Worker | metrics/logs/traces, propagation, cardinality, SLO và profiling | Mở rộng sau khi có luồng E2E |
| 6 | UC-06 — Import, replay và đối soát V1 | Catalog, Query | batch idempotency, checkpoint, reconciliation, rollback an toàn | Sau core flow |

Thứ tự này cố ý đi từ **hệ thống đang có**: Scan chỉ phát hiện và chờ review; Catalog mới tạo dữ liệu canonical; Query chỉ dựng read model. Không bắt đầu bằng một bài upload hoặc một Saga giả định vì chúng chưa phải boundary trung tâm của V2.

## Scale & Capacity Track — các bài toán quy mô

Sáu use case trên trả lời **đúng/sai trong hệ phân tán**. Để luyện phỏng vấn Senior, phải làm thêm một lượt thứ hai trả lời **hệ thống còn đúng không khi volume, traffic và thời gian chạy tăng lên**. Track này không thay thế flow chính: chỉ bắt đầu từng bài khi vertical slice tương ứng đã đúng ở quy mô nhỏ.

> `1 triệu người dùng` không phải workload đủ rõ để thiết kế. Trước mỗi bài phải chốt traffic mix: registered/DAU, peak RPS đọc-ghi, kích thước payload, hot-key ratio, data retention và SLO. Các con số dưới đây là quy mô lab để ép lộ bottleneck, không phải năng lực đã xác nhận của V2.

| ID | Bài toán quy mô | Gắn sau | Câu hỏi Senior phải bảo vệ |
| --- | --- | --- | --- |
| SC-01 | Scan 1 triệu filesystem entry | UC-01 | Làm sao duyệt cây thư mục với memory bị chặn, progress/checkpoint, batch persistence và backpressure mà không biến preview thành một HTTP response khổng lồ? |
| SC-02 | Import/backfill 1 triệu record V1 | UC-01 + UC-02 | Làm sao dry-run, chia batch, checkpoint/restart, dedupe và đối soát khi process chết giữa chừng mà không ghi vào DB V1? |
| SC-03 | Catalog chứa hàng trăm triệu đến 1 tỷ asset | UC-01 | Partition/index/archive chọn theo access pattern nào; uniqueness, migration và truy vấn canonical còn hoạt động ra sao? |
| SC-04 | Query/search trên hàng trăm triệu document với peak read lớn | UC-02 + UC-03 | Read model, pagination, Elasticsearch shard/index lifecycle, cache-key/hot-key và degraded mode bảo vệ SLO thế nào? |
| SC-05 | Outbox/Kafka backlog và replay ở quy mô lớn | UC-01 + UC-02 | Partition key, consumer concurrency, lag, retention, DLT/replay và duplicate/stale event được đo/kiểm soát thế nào? |
| SC-06 | Hàng triệu media-processing job | UC-04 | Bounded concurrency, disk/network saturation, queue backlog, retry budget và fairness tránh làm Worker/Storage quá tải thế nào? |

Chi tiết thứ tự, workload contract và bằng chứng cần thu thập xem tại [Scale & Capacity Track](./use-cases/scale-capacity/README.md). Chỉ sau SC-01 đến SC-04 mới có dữ liệu hợp lý để thảo luận scale-out, sharding hoặc multi-region; không chọn chúng như điểm xuất phát.

## UC-01 — Scan → Catalog canonical ingestion

### Scenario

Admin scan một `rootKey` đã cấu hình, xem proposal/issue, approve một proposal hợp lệ. Scan lưu decision cùng outbox trong `scan_db`; relay phát `media.file.discovered.v1`; Catalog dedupe event rồi upsert Subject/Asset trong `catalog_db`.

### Câu hỏi phỏng vấn cần trả lời được

1. Vì sao Scan không ghi trực tiếp Catalog và tại sao approval là boundary nghiệp vụ?
2. Dual-write bị hỏng ở đâu nếu vừa commit DB vừa gửi Kafka trực tiếp?
3. Outbox thực sự bảo đảm điều gì, còn duplicate/out-of-order phải giải quyết ở đâu?
4. Vì sao key partition khác với `eventId`, và idempotency của Catalog phải nằm cùng transaction nào?
5. Khi Kafka/Catalog hỏng hoặc một message không hợp lệ, người vận hành quan sát, retry/DLT và khôi phục thế nào?

### Evidence hiện có để học từ code thật

- [Scan Service deep-dive](./deep-dive/scan-service/00-overview.md): discovery, proposal/review và ownership `scan_db`.
- [Transactional Outbox deep-dive](./deep-dive/transactional-outbox/README.md): dual-write, at-least-once và consumer idempotency.
- [Contract `media.file.discovered.v1`](../../docs/contracts/events/media.file.discovered.v1.md): producer, consumer, partition key, retry/DLT và compatibility.
- [Scan context](../../apps/scan-service/CONTEXT.md) và [Catalog context](../../apps/catalog-service/CONTEXT.md): invariant đang áp dụng trong V2.

Trước khi tạo thêm nội dung, hãy audit các link/code example trong deep-dive Scan theo contract trên. Ví dụ cũ dùng event `.v2`, port hoặc lệnh vận hành không còn khớp không được dùng làm evidence phỏng vấn.

## UC-02 và UC-03 — Catalog → Query

Hai use case này tách ra để không học Elasticsearch/Redis trước khi hiểu read model.

- **UC-02 — projection correctness:** Catalog phát `media.subject.changed.v1` snapshot; Query dedupe bằng `eventId`, dùng `subjectVersion` để bỏ event stale và thay thế asset projection trong local transaction. Học consistency window, retry/DLT, replay và cách UI biểu đạt dữ liệu chưa hội tụ.
- **UC-03 — read performance:** Query trả list/detail/search từ read model; Elasticsearch là search projection, Redis là cache tối ưu có thể hỏng. Học Fast Hit + hydration, cache-aside, invalidation, reconciliation, index rebuild và benchmark trước/sau.

Study material hiện có: [CQRS Read Projection hub](./deep-dive/cqrs-read-projection/README.md), [contract `media.subject.changed.v1`](../../docs/contracts/events/media.subject.changed.v1.md) và [Query context](../../apps/query-service/CONTEXT.md). Chỉ mở thư mục use case riêng khi UC-01 đã có summary, question bank và evidence test.

## Các use case sau core flow

### UC-04 — Media processing pipeline

Đây là bài toán kế tiếp đúng với [FT013](../../docs/features/013-media-worker-processing-foundation/03-plan.md): Catalog enqueue request cho asset có `storageKey`; Worker stateless đọc file trong root an toàn với concurrency bị giới hạn; completion quay lại Catalog rồi hội tụ Query. Trọng tâm là work queue, deterministic completion, retry/DLT và resource-bound I/O — không phải upload client trực tiếp.

### UC-05 — Observability và performance profiling

Áp dụng sau khi có một luồng E2E để trace/metric có ngữ cảnh nghiệp vụ. Mở rộng từ [Observability hub](./deep-dive/observability/README.md): correlation/trace xuyên HTTP và Kafka, structured log không lộ absolute path, metric cardinality thấp, đo Kafka lag/outbox backlog/cache hit rate. Alert/SLO, k6, JFR/JMC và profiling sâu thuộc phần sau của Phase 8.

### UC-06 — Import, replay và reconciliation

Học cách nạp V1 an toàn: read-only inventory, dry-run, batch idempotency, checkpoint, đối soát canonical Catalog với Query projection và rollback không destructive. Đây là use case Phase 7; không chạy import thật chỉ để tạo bài tập.

## Bài lab để sau, không chen vào core roadmap

| Bài lab | Lý do để sau | Điều kiện mở |
| --- | --- | --- |
| Resumable multipart upload | V2 hiện lấy media từ filesystem qua Scan; chưa có client-upload boundary | Có brief/design thay đổi ingestion boundary và contract |
| Gateway security/rate limit | Kiến trúc hiện không ưu tiên authentication/permission phức tạp | Có threat model, identity provider và traffic objective |
| Circuit breaker/Saga/chaos/Kubernetes | Chưa có long-running distributed transaction cần compensation; Kubernetes không nằm core flow | Có failure mode thật, SLO và môi trường thí nghiệm cô lập |
| Debezium, Schema Registry, Kafka Streams | Là lab evolution, không phải prerequisite cho outbox polling hiện tại | Đã đo bottleneck hoặc có compatibility problem cụ thể |

## Nhịp học cho mỗi use case

1. **Đọc boundary:** system primer, một service context mỗi lần và đúng contract/event liên quan.
2. **Vẽ mental model:** owner, local transaction, input/output và failure point; không đưa implementation detail chưa xác minh vào sơ đồ.
3. **Theo một trace/event thật:** HTTP hoặc scheduled job → DB → outbox → Kafka → consumer → local DB/read API.
4. **Tự phá vỡ giả định:** crash sau local commit, duplicate event, stale event, dependency unavailable và poison message.
5. **Chốt interview pack:** answer 30 giây/2 phút, decision table, red flags và câu hỏi follow-up.
6. **Chỉ rồi mới mở use case kế tiếp.** Ghi link evidence/test, không copy lại design vào roadmap.

## Definition of done cho một use case study

- Có scenario, invariants, owner database/contract và ít nhất một failure test diễn đạt được.
- Deep-dive phủ `WHY → WHAT → HOW → FAILURE → TRADE-OFF → PROJECT → EVOLUTION` và phân biệt general fact, framework behavior, project configuration.
- Có summary ngắn và question bank theo chain `FOUNDATION → SENIOR → ARCHITECT`.
- Các link chỉ đến source of truth hoặc code/evidence thật; không có port, event version, API hay hiệu năng tuyệt đối chưa kiểm chứng.
- Cập nhật index use case và chỉ chuyển trạng thái sang `Đã chốt` khi đủ ba artifact cùng evidence.
