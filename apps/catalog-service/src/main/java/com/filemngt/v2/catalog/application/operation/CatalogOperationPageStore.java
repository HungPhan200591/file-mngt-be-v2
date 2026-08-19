package com.filemngt.v2.catalog.application.operation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
/** Gọi native bounded merge; database tự fence canonical write, snapshot và checkpoint trong cùng transaction. */
public class CatalogOperationPageStore {
    private final JdbcTemplate jdbc;
    private final int maximumSnapshotBytes;
    private final long statementTimeoutMillis;

    public CatalogOperationPageStore(
            JdbcTemplate jdbc,
            @Value("${catalog.operation.maximum-snapshot-bytes:921600}") int maximumSnapshotBytes,
            @Value("${catalog.operation.statement-timeout-ms:20000}") long statementTimeoutMillis,
            @Value("${catalog.operation.lease-seconds:30}") long leaseSeconds) {
        this.jdbc = jdbc;
        if (maximumSnapshotBytes < 1) {
            throw new IllegalArgumentException("catalog.operation.maximum-snapshot-bytes must be positive");
        }
        this.maximumSnapshotBytes = maximumSnapshotBytes;
        if (statementTimeoutMillis < 1 || statementTimeoutMillis >= leaseSeconds * 1_000) {
            throw new IllegalArgumentException("Catalog finalizer statement timeout must be positive and below lease");
        }
        this.statementTimeoutMillis = statementTimeoutMillis;
    }

    @Transactional
    public int finalizePage(CatalogOperationLaneClaim claim, int pageSize) {
        jdbc.queryForObject(
                "select set_config('statement_timeout', ?, true)", String.class, Long.toString(statementTimeoutMillis));
        Integer processed = jdbc.queryForObject(
                "select catalog_finalize_operation_page(?, ?, ?, ?, ?, ?)",
                Integer.class,
                claim.operationId(),
                claim.laneId(),
                claim.owner(),
                claim.fenceToken(),
                pageSize,
                maximumSnapshotBytes);
        return processed == null ? 0 : processed;
    }
}
