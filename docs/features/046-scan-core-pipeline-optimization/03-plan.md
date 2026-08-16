# FT-046 — Plan: Scan-Core Pipeline Optimization & Benchmark Evidence

Status: `READY`  
Owner: `apps/scan-service/`  
Must Preserve: inventory semantics, chunk atomicity, lease fencing, staging cleanup, scan-core benchmark scope.

## 1. Execution capsule

- **Scope / files**:
  - `apps/scan-service/src/main/java/.../ScanExecutionTimeline.java` [MODIFY]
  - `apps/scan-service/src/main/java/.../ScanInventoryStageWriter.java` [MODIFY]
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
4. Add assertions for row equivalence, modified/revived records, cleanup, retry and lease fence.
5. Adopt a query candidate only after runtime evidence shows stable improvement.
6. Run Spotless and static checks; update the benchmark report with scope and environment manifest.

## 3. Verification

```powershell
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=InventoryDiffQueryBenchmarkTest
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest
```

Run these commands only when the user authorizes runtime verification. Record median/min/max across repeated runs and label any unrun check as pending.

## 4. Rollback

Revert the SQL candidate and telemetry-only changes independently. No migration, REST/Kafka contract or cross-service rollback is required.

## 5. Completion gate

`DONE` only after benchmark runtime evidence is attached, correctness assertions pass for all workload classes, and the report clearly separates scan-core evidence from full Scan → Catalog → Query SLO evidence.
