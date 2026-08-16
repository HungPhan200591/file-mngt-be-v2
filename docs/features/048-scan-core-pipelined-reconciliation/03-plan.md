# FT-048 — Plan: Scan-Core Pipelined Reconciliation

Status: `DONE — COLD qualified`
Owner: `apps/scan-service/`  
Must Preserve: one transaction per chunk, ordered checkpoint, lease fence, bounded memory and terminal recovery.

## 1. Execution capsule

- **Scope / files**:
  - `ScanExecutor.java` [MODIFY]
  - new application pipeline coordinator under `application/scan/`
  - `ScanChunkCommitter.java` [MODIFY only if boundary adapter is needed]
  - `ScanExecutionLiveness.java` and deadline guard [MODIFY if required]
  - pipeline correctness/benchmark tests [NEW]
- **Read on demand**: this Plan, [FT-047 Design](../047-scan-core-cold-path/02-design.md), `03-CODING_RULES.md`, scan-service `CONTEXT.md`.
- **Do not load by default**: cross-service BT-09 docs and file 07.

## 2. Implementation steps

1. **DONE** — Introduce immutable chunk envelope with sequence and progress metadata.
2. **DONE** — Add bounded queue with capacity 1; keep capacity configurable for benchmark experiments.
3. **DONE** — Run one producer and one ordered commit consumer.
4. **DONE** — Define cancellation and exception propagation before enabling overlap.
5. **DONE** — Add tests for ordering, empty chunk, queue validation, producer/consumer failure and lease expiry.
6. **DONE** — Benchmark queue capacity 1 and 2 against the FT-047 baseline; select capacity 1.

## 3. Verification

```powershell
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest
```

Latest IntelliJ evidence: COLD completed in 25.371s with capacity 1 and 26.408s with capacity 2. Capacity 1 is selected;
warm scenarios remain regression evidence because their database workload is substantially noisier.

The report must include queue wait, parse time, commit time, heap/live chunk count, lease extensions and total duration.

## 4. Rollback and gate

Keep the sequential execution mode as fallback. COLD correctness is unchanged and repeated runs remain around 25–26s,
below the FT-047 reference of 28.906s, without observed lease or memory failure. FULL_CHANGE and REVIVED remain
regression workloads, not a separate SLO claim.
