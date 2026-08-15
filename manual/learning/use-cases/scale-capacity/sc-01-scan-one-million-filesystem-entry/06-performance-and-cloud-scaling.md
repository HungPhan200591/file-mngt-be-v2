# SC-01 — Phân tích bottleneck và hướng scale Kafka/cloud

> Trạng thái: ghi chú khảo sát, chưa phải quyết định triển khai.
>
> Ngày: 2026-08-12
>
> Vai trò: phân tích hypothesis và evidence. Workload contract nằm ở
> [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md); dependency map
> và task đang mở nằm ở [04-break-task.md](./04-break-task.md). Không dùng file này làm entrypoint
> mặc định hoặc suy ra SLO từ một benchmark đơn lẻ.

## 1. Kết luận chính

Nếu đo **1 triệu file chưa approve**, Kafka không nằm trên đường nóng của
scan-service. Luồng hiện tại là:

```text
Filesystem
  → discovery/staging
  → reconciliation/analyze
  → scan_db
  → proposal review

Approve
  → scan_outbox_event
  → Kafka media.file.discovered.v2
  → Catalog consumer
  → catalog_db
```

Vì vậy cần tách hai bài toán:

| Workload | Nghi ngờ bottleneck đầu tiên |
| --- | --- |
| Scan 1M file chưa approve | Filesystem, Docker/VM disk, parser, PostgreSQL, materialize/analyze/finalize |
| Approve số lượng lớn | Outbox publisher, Kafka partition/ack, Catalog consumer và Catalog DB |

Không nên kết luận Kafka là nguyên nhân chỉ từ thời gian tổng. Cần phân rã
timeline và consumer lag trước.

## 2. Bằng chứng hiện có trong Backend V2

- `scan-service` chỉ tạo event `media.file.discovered.v2` sau approval.
- Outbox dùng claim bounded với `FOR UPDATE SKIP LOCKED` và lease; publish nằm
  ngoài transaction; consumer dedupe theo `eventId`.
- `ScanOutboxPublisher` hiện publish từng event trong vòng lặp.
- `KafkaOutboxMessagePublisher` gọi `kafka.send(record).join()` cho từng event,
  nên đường publish của một publisher là tuần tự theo lần broker acknowledge.
- Catalog listener hiện chỉ khai báo một `@KafkaListener`; chưa có bằng chứng
  runtime về concurrency hoặc số partition của topic.
- Local Compose chạy một Kafka broker kiêm controller, replication factor bằng 1,
  một volume dữ liệu. Đây là môi trường dev, không phải cluster production.

## 3. Benchmark cần đọc đúng ngữ cảnh

Backend đã có bằng chứng cold scan 1M dưới 30 giây trong FT-031, nhưng đây là
benchmark có điều kiện và không phải SLO chung cho mọi máy.

Một benchmark production-like khác ghi nhận tổng 84,651 giây; filesystem-only
khoảng 17,832 giây. Persistence set-based gần 18,674–19,348 giây cho workload
riêng. Các con số này không đo năng lực Kafka end-to-end.

Không được suy ra capacity 1M chỉ từ batch size, virtual threads hoặc một lần
chạy thành công. Cần benchmark riêng filesystem, PostgreSQL, outbox/Kafka và
Catalog handler.

## 4. Cách xác định bottleneck lần sau

### 4.1 Scan chưa approve

Ghi lại các pha: discovery, staging COPY, materialize diff, analyze, proposal /
issue persistence và finalize. So sánh hai máy về:

- loại ổ đĩa và vị trí fixture (NVMe, HDD, network drive, Docker bind mount);
- CPU/RAM, Windows Power Mode, antivirus/indexing;
- Docker Desktop CPU/RAM/disk sharing và vị trí Docker volume;
- JDK/Java process, PostgreSQL CPU/WAL/lock/connection pool;
- cold cache và warm cache.

### 4.2 Approve và Kafka

Theo dõi cùng một `runId`/correlation:

| Tầng | Chỉ số |
| --- | --- |
| Scan outbox | pending count, oldest pending age, publish latency, retry/failure |
| Kafka | partition count, records/sec, bytes/sec, producer latency, consumer lag |
| Catalog | handler p50/p95, transaction time, DB CPU, lock wait, pool exhaustion |
| DLT | retry count, DLT records, duplicate event IDs |

Thí nghiệm tối thiểu: tách thời gian scan kết thúc khỏi thời gian backlog
outbox/Catalog bắt kịp; nếu tắt consumer mà scan vẫn chậm, Kafka không phải
bottleneck của scan.

## 5. Scale trên hệ thống lớn

Kafka scale theo nhiều trục, không chỉ bằng cách tăng CPU broker:

1. Tăng số partition để tạo nhiều lane publish/consume.
2. Chạy nhiều consumer instance/concurrency; một consumer group chỉ tận dụng tối
   đa số partition.
3. Chạy nhiều outbox publisher replica; claim bằng `SKIP LOCKED` để chia record.
4. Producer dùng async send có giới hạn in-flight, batching, compression và
   backpressure; tránh chờ tuần tự từng record.
5. Chọn partition key giữ ordering theo subject nhưng tránh một key duy nhất làm
   hot partition.
6. Scale Catalog consumer cùng Catalog DB; nếu handler vẫn transaction từng event,
   thêm consumer chỉ chuyển bottleneck sang database.
7. Autoscale theo consumer lag, backlog age, handler p95 và DB wait/CPU; không
   autoscale chỉ theo CPU Kafka.
8. Giữ at-least-once + idempotency + retry/DLT. Không giữ transaction DB mở trong
   lúc chờ Kafka để cố đạt exactly-once.

Với hàng triệu file, hệ thống lớn thường tạo manifest/chunk theo prefix hoặc
range, chạy nhiều scan worker, checkpoint durable và chỉ phát event nghiệp vụ
sau khi file đã được phân loại/approve. Kafka là transport/distribution plane,
không thay thế filesystem scan hay bulk database processing.

## 6. Local khác cloud ở đâu

| Local Compose | Production cloud |
| --- | --- |
| Một broker/controller | Nhiều broker, nhiều AZ/zone |
| Replication factor 1 | Replication và failover |
| Disk laptop/Docker volume | SSD/network storage có throughput dự kiến |
| Một publisher/consumer | Nhiều replica, autoscaling |
| Không có quota/SLO capacity | Quota, metrics, alert và capacity planning |
| Tự quản lý broker | Managed Kafka hoặc streaming service |

Ví dụ managed service:

- AWS MSK Express có storage tự mở rộng, throughput/broker cao hơn và hỗ trợ
  scale/rebalance nhanh hơn: [MSK Express brokers](https://docs.aws.amazon.com/msk/latest/developerguide/msk-broker-types-express.html).
- Google Managed Service for Apache Kafka tự provision/rescale broker theo tổng
  vCPU/RAM và rebalance partition khi cluster tăng: [Google Managed Kafka](https://docs.cloud.google.com/managed-service-for-apache-kafka/docs/overview).
- Azure Event Hubs dùng partition để song song hóa và tách capacity namespace
  khỏi số partition; số partition cũng là trần consumer parallelism:
  [Event Hubs features](https://learn.microsoft.com/en-us/azure/event-hubs/event-hubs-features).

Managed Kafka giảm vận hành broker, storage, AZ và rebalance; nó không tự sửa
partition key sai, consumer handler chậm, transaction quá nhỏ hoặc filesystem
đầu vào chậm.

## 7. Việc tiếp theo khi quay lại

1. Xác nhận đang đo scan chưa approve hay approve 1M tới `QUERY_DB_READY`.
2. Với approve, chạy workload ladder 1K → 5K → 50K → 250K → 1M; 5K chỉ là calibration rung.
3. Lấy phase timing, outbox backlog, Kafka lag, Catalog/Query transaction và watermark trên cùng một operation.
4. Chỉ sau khi có evidence mới lập hypothesis tuning: async producer, topic partitions, consumer
   concurrency, Catalog coalesce hoặc Query bulk projection.

Các kiểm tra build, Testcontainers, migration, runtime và benchmark mới vẫn là
deferred cho tới khi được yêu cầu rõ ràng.
