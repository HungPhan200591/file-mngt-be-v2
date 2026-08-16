# FT-049 — Plan: Scan-Core Scale Qualification

Status: `READY`  
Owner: `apps/scan-service/`  
Must Preserve: benchmark scope, reproducibility, correctness gates and evidence separation.

## 1. Execution capsule

- **Scope / files**:
  - `ScanCorePipelineBenchmarkTest.java` [EXTEND]
  - benchmark fixture/generator [EXTEND]
  - `benchmark/results/` [ADD qualification report]
  - `benchmark/BENCHMARK_RESULTS.md` [UPDATE summary only]
- **Dependencies**: FT-046, FT-047 and FT-048 must have runtime evidence or an explicit deferred status.
- **Read on demand**: this Plan, preceding FT Plans, benchmark README and scan-service context.
- **Do not load by default**: file 07 deep-dive content; use only its approved target when qualification is explicitly requested.

## 2. Implementation steps

1. Parameterize workload size and dataset state without duplicating fixture logic.
2. Add deterministic seed and run manifest output.
3. Run warm-up plus repeated measurements for each matrix cell.
4. Capture phase timings, throughput, resource observations and correctness assertions.
5. Produce one detailed report and one short dashboard row per implementation variant.
6. Mark each claim as measured, inferred or pending.

## 3. Verification

```powershell
.\mvnw.cmd test -Pbenchmark -pl apps/scan-service -Dtest=ScanCorePipelineBenchmarkTest
```

Runtime execution is a separate user-authorized step. The report must preserve failed runs and explain exclusions; do not overwrite a previous result without a new run identifier.

## 4. Rollback and completion gate

Rollback is documentation-only for benchmark artifacts; source fallback remains the sequential path from FT-048. Mark `DONE` only after the scale matrix, correctness evidence and hardware envelope are attached. Any missing cell keeps the qualification partial.
