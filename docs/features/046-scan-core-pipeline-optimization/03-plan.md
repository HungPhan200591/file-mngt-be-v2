# FT-046 — Plan: Scan-Core Pipeline Optimization & Benchmark Evidence

Status: `DONE — benchmark waiver accepted`
Owner: `apps/scan-service/`  
Must Preserve: inventory semantics, chunk atomicity, lease fencing, staging cleanup, scan-core benchmark scope.

## 1. Execution capsule

- **Scope / files**:
  - `apps/scan-service/src/main/java/.../ScanExecutionTimeline.java` [MODIFY]
  - `apps/scan-service/src/main/java/.../ScanInventoryStageWriter.java` [MODIFY]
  - `apps/scan-service/src/main/java/.../ScanFileInventorySetWriter.java` [MODIFY]
  - `apps/scan-service/src/main/resources/db/migration/V21__add_inventory_diff_new_flag.sql` [NEW]
  - `apps/scan-service/src/test/java/.../benchmark/pipeline/ScanCorePipelineBenchmarkTest.java` [MODIFY]
  - `apps/scan-service/src/test/java/.../benchmark/pipeline/InventoryDiffQueryBenchmarkTest.java` [NEW]
  - `apps/scan-service/src/test/java/.../benchmark/fixture/` [EXTEND]
  - `apps/scan-service/src/test/java/.../benchmark/results/` [ADD REPORT]
- **Read on demand**: this Plan, [01-brief.md](./01-brief.md), [02-design.md](./02-design.md), scan-service `CONTEXT.md`, `03-CODING_RULES.md`.
- **Do not load by default**: manual deep-dive, historical FT docs and [07-performance-slo-and-benchmarks.md](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/07-performance-slo-and-benchmarks.md).

## 2. Implementation steps

1. Add exact phase boundaries to runtime timeline and terminal benchmark logging.
2. Extend fixtures to seed cold, unchanged, incremental-change and full-change inventory states.
3. Add a focused diff-query benchmark with the current SQL and semantic-equivalent candidates.
4. Materialize `is_new` once in the diff scratch stage and remove warm per-chunk inventory anti-join. V21 backfills existing scratch rows before enforcing `NOT NULL`.
5. Add assertions for row equivalence, modified/revived records, cleanup, retry and lease fence.
6. Adopt a query candidate only after runtime evidence shows stable improvement.
7. Run Spotless and static checks; update the benchmark report with scope and environment manifest.

## 3. Verification

```powershell
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=InventoryDiffQueryBenchmarkTest
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest
```

Run these commands only when the user authorizes runtime verification. Record median/min/max across repeated runs and label any unrun check as pending.

Runtime evidence `2026-08-17`: focused diff-query assertions pass cho 5 workload; full scan-core 1M trả `COMPLETED` cho `COLD`, `UNCHANGED`, `INCREMENTAL`, `FULL_CHANGE` và `REVIVED`. User chấp nhận chốt feature với benchmark waiver; repeated-run median/min/max vẫn là evidence gap của SLO, không phải qualification percentile.

## 4. Rollback

Revert the SQL candidate and telemetry-only changes independently. No migration, REST/Kafka contract or cross-service rollback is required.

## 5. Completion gate

`DONE`: benchmark runtime evidence đã được ghi và correctness assertions đã pass cho các workload. Waiver đã chấp nhận thiếu median/min/max; report vẫn tách scan-core khỏi full Scan → Catalog → Query SLO evidence.
