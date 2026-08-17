# FT-050 — Plan: Approval Preparation & Persistence Acceleration

Status: `IMPLEMENTED — VERIFY PENDING`  
Owner: `apps/scan-service/`  
Must preserve: one DB writer, transactional decision/outbox/checkpoint, operation cursor, lease fence,
event contract and bounded memory.

## 1. Execution capsule

- **Scope**: approval preparation/persistence only; no REST, Kafka schema, Catalog or Query change.
- **Main files**:
  - `application/decision/ScanDecisionChunkPreparation.java` [NEW]
  - `ScanDecisionChunkExecutor.java`, `ScanDecisionChunkWriter.java` [MODIFY]
  - `ScanDecisionJdbcRepository.java` [MODIFY]
  - `ScanOutboxEventFactory.java`, `ScanFileInventoryRepository.java` [MODIFY]
  - `ApprovalOperationProperties.java`, `application.yml`, migrations `V23` and `V24` [MODIFY/NEW]
  - focused unit tests and approval benchmark [MODIFY/NEW]
- **Read on demand**: [FT-045 Plan](../045-scan-decision-chunking/03-plan.md),
  [SC-01 architecture section 7](../../architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md#7-scan-approval-1m),
  scan-service `CONTEXT.md` and `03-CODING_RULES.md`.

## 2. Implementation steps

1. Add bounded `preparation-parallelism`, COPY fallback configuration and durable proposal cutoff.
2. Add one inventory repository query for missing deletion paths per chunk.
3. Create immutable prepared chunk/partition executor with virtual-thread tasks, deterministic merge and sibling
   cancellation on failure.
4. Route `ScanDecisionChunkExecutor` through preparation before calling the existing transaction writer.
5. Add COPY decision/outbox writers; select COPY or JDBC fallback in the transaction writer.
6. Add approval cursor index migration.
7. Add unit tests for partition order, bulk delete validation and preparation failure; enrich benchmark output with
   active strategy/parallelism.
8. Run targeted formatter/test/benchmark only when authorized; record measured result separately from the
   historical 121-second evidence.

Preliminary benchmark evidence after the FT-050 acceleration changes (before the subsequent V24 cutoff change):
`81,774 ms` for 1,000,000 rows,
`copyEnabled=true`, `preparationParallelism=4`, PostgreSQL `18.0-alpine`, throughput `12,229 records/s`.
This remains Scan-only evidence and must be rerun after V24 before marking runtime verification complete.

## 3. Verification

Static gate:

```powershell
git diff --check
```

When benchmark authorization is granted:

```powershell
./mvnw.cmd test -Pbenchmark -pl apps/scan-service '-Dtest=ApprovalDecisionChunkingBenchmarkTest'
```

Compare at least JDBC fallback / COPY and preparation parallelism `1`, `2`, `4`, `8` with the same fixture,
container image and review projection disabled. Report total time plus preparation/persistence phase evidence;
do not claim `QUERY_DB_READY` from this benchmark.

## 4. Rollback

- Immediate runtime rollback: `SCAN_APPROVAL_OPERATION_COPY_ENABLED=false` and
  `SCAN_APPROVAL_OPERATION_PREPARATION_PARALLELISM=1`.
- Source rollback keeps migration append-only. Reverting code after migration leaves the new index harmless;
  remove it only through a later migration after scan-core impact evidence.
- No event/API compatibility rollback is required because the feature does not change either contract.
