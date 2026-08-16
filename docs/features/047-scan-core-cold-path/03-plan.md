# FT-047 — Plan: Scan-Core Cold Path Without Diff Stage

Status: `READY`  
Owner: `apps/scan-service/`  
Must Preserve: warm reconciliation, snapshot cleanup, chunk atomicity, lease fencing and retry semantics.

## 1. Execution capsule

- **Scope / files**:
  - `ScanExecutor.java` [MODIFY]
  - `ScanChunkCommitter.java` [MODIFY]
  - `ScanInventoryStageWriter.java` [MODIFY]
  - `ScanReconciliationPageReader.java` [MODIFY/NEW adapter path]
  - `ScanCorePipelineBenchmarkTest.java` [MODIFY]
  - cold-path correctness tests [NEW/MODIFY]
- **Read on demand**: this Plan, [FT-046 Plan](../046-scan-core-pipeline-optimization/03-plan.md), Scan Service `CONTEXT.md`, `03-CODING_RULES.md`.
- **Do not load by default**: file 07 and historical staging deep-dives.

## 2. Implementation steps

1. Represent reconciliation source explicitly as `COLD_STAGE` or `WARM_DIFF`.
2. Skip `materializeDiff` only for a validated cold root.
3. Read cold pages from `scan_inventory_stage` using the same bounded keyset contract.
4. Preserve existing chunk commit, progress and finalization boundaries.
5. Add cold failure, retry, cleanup and mode-race tests.
6. Run formatter and targeted static checks, then benchmark against FT-046.

## 3. Verification

```powershell
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest
```

Record cold total/phase timings and exact persisted counts. Warm workloads must be rerun to detect regression.

## 4. Rollback and gate

Rollback is a feature-flag/configuration fallback to the existing staged diff path. Mark `DONE` only when cold and warm correctness pass and runtime improvement is measured; otherwise keep the implementation behind the fallback.
