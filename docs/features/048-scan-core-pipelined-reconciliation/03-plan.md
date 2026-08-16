# FT-048 — Plan: Scan-Core Pipelined Reconciliation

Status: `READY`  
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

1. Introduce immutable chunk envelope with sequence, lease context and progress metadata.
2. Add bounded queue with capacity 1; make capacity configurable only for benchmark experiments.
3. Run one producer and one ordered commit consumer.
4. Define cancellation and exception propagation before enabling overlap.
5. Add tests for queue full, producer failure, consumer failure, lease expiry and shutdown.
6. Benchmark queue capacity 1 and 2 against the FT-047 baseline.

## 3. Verification

```powershell
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest
```

The report must include queue wait, parse time, commit time, heap/live chunk count, lease extensions and total duration.

## 4. Rollback and gate

Keep a sequential execution mode as fallback. Mark `DONE` only when correctness is unchanged and the median improvement survives repeated cold/warm runs without violating lease or memory budgets.
