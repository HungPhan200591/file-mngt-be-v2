# FT-047 — Plan: Scan-Core Cold Path Without Diff Stage

Status: `DONE — benchmark waiver accepted`
Owner: `apps/scan-service/`  
Must Preserve: warm reconciliation, snapshot cleanup, chunk atomicity, lease fencing and retry semantics.

## 1. Execution capsule

- **Scope / files**:
  - `ScanExecutor.java` [MODIFY]
  - `ScanChunkCommitter.java` [MODIFY]
  - `ScanReconciliationPreparer.java` [NEW]
  - `ScanReconciliationPreparation.java`, `ScanReconciliationSource.java` [NEW]
  - `ScanInventoryStageWriter.java` [MODIFY]
  - `ScanInventoryStageReader.java` [NEW]
  - `ScanReconciliationPageReader.java` [MODIFY]
  - `ScanReconciliationExecutor.java`, `ScanReconciliationRequest.java` [NEW]
  - `ScanCorePipelineBenchmarkTest.java` [MODIFY]
  - `ScanReconciliationPreparerTest.java`, `ScanReconciliationPageReaderTest.java` [NEW]
- **Read on demand**: this Plan, [FT-046 Plan](../046-scan-core-pipeline-optimization/03-plan.md), Scan Service `CONTEXT.md`, `03-CODING_RULES.md`.
- **Do not load by default**: file 07 and historical staging deep-dives.

## 2. Implementation steps

1. Represent reconciliation source explicitly as `COLD_STAGE` or `WARM_DIFF`.
2. Skip `materializeDiff` only for a validated cold root.
3. Read cold pages from `scan_inventory_stage` using the same bounded keyset contract.
4. Preserve existing chunk commit, progress and finalization boundaries.
5. Add cold failure, retry, cleanup and mode-race tests.
6. Run formatter and targeted static checks, then benchmark against FT-046.

Implementation `2026-08-17`: `COLD_STAGE` reads and writes the discovery snapshot directly; `WARM_DIFF` retains the FT-046 diff path. Unit regressions cover source selection and page routing. Một run đạt cold `28.906 ms` / `34.595 files/s`; user chấp nhận benchmark waiver cho biến động warm giữa các process.

## 3. Verification

```powershell
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest
```

Record cold total/phase timings and exact persisted counts. Warm variance remains a follow-up observation, not a blocker for this accepted benchmark waiver.

## 4. Rollback and gate

Rollback is a feature-flag/configuration fallback to the existing staged diff path. `DONE` is accepted with the user-approved benchmark waiver; percentile and repeated-run SLO qualification remain outside this feature gate.
