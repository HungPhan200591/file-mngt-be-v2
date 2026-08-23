# Scan Service — Combined End-to-End Pipeline

Status: `PASS — MEASURED`

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
- 64 logical completion shards, 16 approval workers, 64 relay lanes/16 workers và 12 Kafka discovery partitions.

## Workloads và kết quả đo đạc

| Workload | Expected discovery events | Expected shard markers | Expected watermark | Scan Core | Approval | Outbox $\rightarrow$ ACK | Total Pipeline | Throughput | Result |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| **25.000** | 25.000 | 64 | 1 | `1.655 ms` | `3.331 ms` | `3.523 ms` | `5.178 ms` | **4.828 records/s** | `PASS` |
| **250.000** | 250.000 | 64 | 1 | `10.320 ms` | `22.354 ms` | `23.254 ms` | `33.575 ms` | **7.446 records/s** | `PASS` |
| **1.000.000** | 1.000.000 | 64 | 1 | `28.703 ms` | `185.117 ms` | `202.194 ms` | `230.898 ms` | **4.331 records/s** | `PASS` |

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
