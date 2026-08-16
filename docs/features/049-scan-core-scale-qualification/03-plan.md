# FT-049 — Plan: Scan-Core Scale Qualification

Status: `DEFERRED — intentionally skipped`
Owner: `apps/scan-service/`  
Must Preserve: benchmark scope, reproducibility, correctness gates and evidence separation.

## 1. Decision

FT-049 is intentionally deferred. The current SC-01 workstream already has 1M scan-core evidence for FT-046–048;
the full scale ladder would add a long, noisy benchmark cycle without changing the selected implementation or current
scope. No production code, SLO contract or file 07 is changed by this decision.

## 2. Execution capsule

- **Scope / files**:
  - `ScanCorePipelineBenchmarkTest.java` [EXTEND]
  - benchmark fixture/generator [EXTEND]
  - `benchmark/results/` [ADD qualification report]
  - `benchmark/BENCHMARK_RESULTS.md` [UPDATE summary only]
- **Dependencies**: FT-046, FT-047 and FT-048 must have runtime evidence or an explicit deferred status.
- **Read on demand**: this Plan, preceding FT Plans, benchmark README and scan-service context.
- **Do not load by default**: file 07 deep-dive content; use only its approved target when qualification is explicitly requested.

## 3. Implementation steps

1. **DEFERRED** — Parameterize workload size and dataset state.
2. **DEFERRED** — Add deterministic seed and run manifest output.
3. **DEFERRED** — Run warm-up plus repeated measurements for each matrix cell.
4. **DEFERRED** — Capture phase timings, throughput, resource observations and correctness assertions.
5. **DEFERRED** — Produce a separate qualification report and dashboard row.
6. **DONE** — Keep existing FT-046–048 evidence separate from a missing scale qualification.

## 4. Verification

Runtime execution was intentionally not started. Existing benchmark reports remain unchanged and are not relabeled as
FT-049 qualification evidence.

## 5. Rollback and completion gate

No rollback is required because no FT-049 source or benchmark implementation was added. Reopen FT-049 only when a
scale ladder, hardware envelope and repeated correctness evidence are explicitly needed.
