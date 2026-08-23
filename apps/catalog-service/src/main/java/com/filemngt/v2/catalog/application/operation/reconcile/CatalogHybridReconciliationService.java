package com.filemngt.v2.catalog.application.operation.reconcile;

import com.filemngt.v2.catalog.application.operation.CatalogOperationFinalizerTelemetry;
import com.filemngt.v2.catalog.application.operation.CatalogOperationUnitClaim;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Điều phối read và pure reduction ngoài transaction trước khi giao bounded result cho persistence. */
@Service
public class CatalogHybridReconciliationService {
    private final CatalogHybridInputStore inputStore;
    private final CatalogHybridReducer reducer;
    private final CatalogHybridPersistenceService persistence;
    private final CatalogOperationFinalizerTelemetry telemetry;
    private final int maximumInputRows;

    public CatalogHybridReconciliationService(
            CatalogHybridInputStore inputStore,
            CatalogHybridReducer reducer,
            CatalogHybridPersistenceService persistence,
            CatalogOperationFinalizerTelemetry telemetry,
            @Value("${catalog.operation.maximum-input-records-per-page:25000}") int maximumInputRows) {
        this.inputStore = inputStore;
        this.reducer = reducer;
        this.persistence = persistence;
        this.telemetry = telemetry;
        if (maximumInputRows < 1) {
            throw new IllegalArgumentException("catalog.operation.maximum-input-records-per-page must be positive");
        }
        this.maximumInputRows = maximumInputRows;
    }

    public int reconcile(CatalogOperationUnitClaim claim) {
        long readStarted = System.nanoTime();
        var input = inputStore.readUnit(claim.operationId(), claim.unitId(), maximumInputRows);
        long readNanos = System.nanoTime() - readStarted;
        if (input.size() > maximumInputRows) {
            throw new IllegalStateException("CATALOG_HYBRID_PAGE_INPUT_LIMIT_EXCEEDED");
        }

        long reduceStarted = System.nanoTime();
        var reduced = reducer.reduce(input);
        long reduceNanos = System.nanoTime() - reduceStarted;

        var persisted = persistence.persist(claim, reduced);
        telemetry.recordHybridPhases(readNanos, reduceNanos, persisted.copyNanos(), persisted.applyNanos());
        return persisted.processed();
    }
}
