# Catalog Service Benchmark Results Dashboard

## FT-059 — Sequential physical-feasibility lower-bound

| Workload | Boundary | Result | Status |
| --- | --- | ---: | --- |
| 1M input / 100K subject | Ingest → reduction → bulk upsert → create outbox → immediate-ack relay; tuần tự, không scheduler/Kafka | `171.871 ms` / `5.818 input/s` | **LOCAL SERIAL SHAPE INFEASIBLE** cho 1M/120s |

Ingest `68.472 ms` + bulk upsert `62.902 ms` đã vượt 120 giây; toàn run có zero lock wait/deadlock và
CPU directional sample thấp, nên evidence chỉ tới serial execution shape, không kết luận phần cứng local đã hết
capacity. Chi tiết resource/WAL/temp/GC: [06-ft059-sequential-physical-feasibility.md](./results/06-ft059-sequential-physical-feasibility.md).

## FT-058 — Combined Catalog reliability gate

| Workload | Clock | Target | Result | Status |
| --- | --- | --- | --- | --- |
| 25K input records | `resumeToFinalAckMs` | Diagnostic calibration | `4.935 ms` / `5.066 rec/s` | PASS correctness; indicators 30K/40K đều không đạt |
| 1M input records | `resumeToFinalAckMs` | `<= 120.000 ms` | `> 120.000 ms`; còn `RECONCILING` | **FEASIBILITY_FAILED** — unit SQL lặp lại statement timeout 20 giây |

Chi tiết run và failure boundary: [05-ft058-reliability-hardening.md](./results/05-ft058-reliability-hardening.md).
`resumeToFinalAckMs` là clock gate bảo thủ; `firstPersistToFinalAckMs` chỉ để bóc phase. Không ghi số liệu
1M vì operation không đạt terminal success và không có final broker acknowledgement.

## FT-054 — Historical legacy/direct baseline

| Benchmark | Legacy 25K | Legacy 1M | Candidate FT054 | Status |
| --- | ---: | ---: | ---: | --- |
| Catalog record processing | 59 rec/s (423.898 ms) | TIMED OUT (> 2m) | 4,325 rec/s (5,781 ms) / 1M TIMED OUT (> 5m) | Historical evidence; không phải FT-057 comparison |

Chi tiết workload và boundary: [01-ft054-legacy-catalog-record-baseline.md](./results/01-ft054-legacy-catalog-record-baseline.md).

## FT-056 — Historical V19–V22 merge evidence

| Workload | mergeMs | Throughput | pageExec | Status |
| --- | ---: | ---: | --- | --- |
| 2.500 subjects (25K events) | 2.032 s | 1.230 subject/s | avg 106ms, p95 155ms, 64 pages | Local evidence 2026-08-21 |
| 100.000 subjects (1M events) | TIMED OUT (> 2 min) | — | — | JUnit timeout 2 min |

V20 hash-join: 25K `2.633 s`, 1M connection failure. V21 nested-loop: user report chậm hơn V19, 1M timeout.
V22 typed reduction/direct merge: 25K `39.278 s`, 1M timeout. Chi tiết: [03-ft056-set-based-cte-merge.md](./results/03-ft056-set-based-cte-merge.md).

## FT-055 — Historical Kafka-to-stage diagnostic

| Workload | drainMs | Throughput | Status |
| --- | ---: | ---: | --- |
| 25K | 1.164 s | 21.478 rec/s | Local evidence 2026-08-21 |
| 1M | 24.527 s | 40.771 rec/s | Testcontainers `fsync=off`, 8 partition / 8 consumer / slice 5000 |

`drainMs` không gồm assignment/produce/rebalance và không gồm finalizer/relay. Chi tiết:
[02-ft055-kafka-backlog-drain.md](./results/02-ft055-kafka-backlog-drain.md).
