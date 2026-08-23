# Scan Service — Combined End-to-End Pipeline

Status: `READY — NOT MEASURED`

## Boundary

Clock bắt đầu ngay trước `ScanService.start()` và kết thúc khi toàn bộ outbox của approval operation có
`published_at`, chỉ sau Kafka broker acknowledgement:

```text
Synthetic cursor → production scan/reconciliation → proposal
                 → production approval workers → decision/outbox
                 → production lane relay → Kafka broker ACK → durable published_at
```

- Production beans thật: `ScanService`, scan executor/reconciliation, `ApprovalOperationService`,
  `ApprovalOperationWorker`, `ScanOutboxLaneRelayScheduler`, `KafkaOutboxMessagePublisher`.
- PostgreSQL 18 và Kafka `apache/kafka-native:3.8.0` chạy bằng Testcontainers.
- Chỉ thay hai external boundary: filesystem bằng deterministic in-memory cursor và Catalog HTTP bằng Mockito.
- Dataset không có parse issue nên `input = proposal = decision = media.file.discovered.v2`.
- 64 logical completion shards, 4 approval workers, 64 relay lanes/4 workers và 12 Kafka discovery partitions.
- Shared timeout 5 phút cho toàn pipeline; timeout log durable run/operation/shard/outbox snapshot.
- `scan.approval-operation.total-deadline-seconds` không override trong test, nên production deadline hiện hành 120 giây
  vẫn là failure gate của approval 1M; 5 phút chỉ là giới hạn chờ/diagnostic của JUnit benchmark.

## Workloads và gate

| Workload | Expected discovery events | Expected shard markers | Expected watermark | Result |
| ---: | ---: | ---: | ---: | --- |
| 25.000 | 25.000 | 64 | 1 | `PENDING` |
| 250.000 | 250.000 | 64 | 1 | `PENDING` |
| 1.000.000 | 1.000.000 | 64 | 1 | `PENDING` |

Mỗi workload chỉ PASS khi scan run `COMPLETED`, approval operation `APPROVAL_COMMITTED`, exact durable
cardinality đúng cho proposal/decision/từng event type, mọi shard `COMPLETED` và pending outbox bằng 0 sau broker ACK.

## IntelliJ/Maven command

Chạy từ project root với JDK `corretto-25.0.4` và Docker Desktop đang chạy:

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\corretto-25.0.4'
$mavenArgs = @(
  '-Pbenchmark',
  '-pl', 'apps/scan-service',
  '-am',
  '-Dtest=ScanEndToEndBenchmarkTest',
  '-Dsurefire.failIfNoSpecifiedTests=false',
  'test'
)
& .\mvnw.cmd @mavenArgs
```

Có thể thay `-Dtest` bằng `ScanEndToEndBenchmarkTest#<method>` để chạy riêng một workload. Không điền kết quả
vào dashboard trước khi method tương ứng đạt durable terminal gate.
