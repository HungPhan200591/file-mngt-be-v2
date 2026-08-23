package com.filemngt.v2.catalog.application.operation.reconcile;

import com.filemngt.v2.catalog.adapter.out.persistence.operation.CatalogHybridReductionCopyWriter;
import com.filemngt.v2.catalog.application.operation.CatalogOperationUnitClaim;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Giữ COPY, canonical mutation, outbox và durable checkpoint trong đúng một fenced transaction. */
@Service
public class CatalogHybridPersistenceService {
    private final JdbcTemplate jdbc;
    private final CatalogHybridReductionCopyWriter copyWriter;
    private final int maximumSnapshotBytes;
    private final long statementTimeoutMillis;

    public CatalogHybridPersistenceService(
            JdbcTemplate jdbc,
            CatalogHybridReductionCopyWriter copyWriter,
            @Value("${catalog.operation.maximum-snapshot-bytes:921600}") int maximumSnapshotBytes,
            @Value("${catalog.operation.statement-timeout-ms:20000}") long statementTimeoutMillis,
            @Value("${catalog.operation.lease-seconds:30}") long leaseSeconds) {
        this.jdbc = jdbc;
        this.copyWriter = copyWriter;
        if (maximumSnapshotBytes < 1) {
            throw new IllegalArgumentException("catalog.operation.maximum-snapshot-bytes must be positive");
        }
        if (statementTimeoutMillis < 1 || statementTimeoutMillis >= leaseSeconds * 1_000) {
            throw new IllegalArgumentException("Catalog finalizer statement timeout must be positive and below lease");
        }
        this.maximumSnapshotBytes = maximumSnapshotBytes;
        this.statementTimeoutMillis = statementTimeoutMillis;
    }

    @Transactional
    public PersistenceResult persist(CatalogOperationUnitClaim claim, CatalogHybridReducedPage reduced) {
        configureTimeout();
        long copyStarted = System.nanoTime();
        var copied = copyWriter.copy(reduced);
        long copyNanos = System.nanoTime() - copyStarted;
        assertCopyCardinality(reduced, copied);

        long applyStarted = System.nanoTime();
        int changed = apply(claim);
        int processed = finalizeUnit(claim, changed);
        return new PersistenceResult(processed, copyNanos, System.nanoTime() - applyStarted);
    }

    private void configureTimeout() {
        jdbc.queryForObject(
                "select set_config('statement_timeout', ?, true)", String.class, Long.toString(statementTimeoutMillis));
    }

    private void assertCopyCardinality(
            CatalogHybridReducedPage reduced, CatalogHybridReductionCopyWriter.CopyResult copied) {
        if (copied.subjects() != reduced.subjects().size() || copied.assets() != reduced.assetCount()) {
            throw new IllegalStateException("Catalog hybrid COPY cardinality mismatch");
        }
    }

    private int apply(CatalogOperationUnitClaim claim) {
        Integer changed = jdbc.queryForObject(
                "select catalog_apply_hybrid_reconciliation_unit(?, ?, ?, ?)",
                Integer.class,
                claim.operationId(),
                claim.unitId(),
                claim.owner(),
                claim.fenceToken());
        return changed == null ? 0 : changed;
    }

    private int finalizeUnit(CatalogOperationUnitClaim claim, int changed) {
        Integer processed = jdbc.queryForObject(
                "select catalog_finalize_hybrid_reconciliation_unit(?, ?, ?, ?, ?, ?)",
                Integer.class,
                claim.operationId(),
                claim.unitId(),
                claim.owner(),
                claim.fenceToken(),
                maximumSnapshotBytes,
                changed);
        return processed == null ? 0 : processed;
    }

    public record PersistenceResult(int processed, long copyNanos, long applyNanos) {}
}
