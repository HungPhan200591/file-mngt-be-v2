package com.filemngt.v2.scan.application.scan.reconciliation;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventorySetWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.adapter.out.persistence.timeout.ScanTransactionTimeouts;
import com.filemngt.v2.scan.application.exception.ScanLeaseExpiredException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Chọn cold/warm reconciliation sau discovery trong một lease-validated transaction. */
@Component
public class ScanReconciliationPreparer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanReconciliationPreparer.class);

    private final ScanRunRepository runs;
    private final ScanFileInventorySetWriter inventoryWriter;
    private final ScanInventoryStageWriter stageWriter;
    private final ScanTransactionTimeouts timeouts;

    public ScanReconciliationPreparer(
            ScanRunRepository runs,
            ScanFileInventorySetWriter inventoryWriter,
            ScanInventoryStageWriter stageWriter,
            ScanTransactionTimeouts timeouts) {
        this.runs = runs;
        this.inventoryWriter = inventoryWriter;
        this.stageWriter = stageWriter;
        this.timeouts = timeouts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ScanReconciliationPreparation prepare(UUID runId, String workerId, String rootKey, boolean overwriteExisting) {
        long startedNanos = System.nanoTime();
        timeouts.applyMutationTimeout();
        validateLease(runId, workerId);
        stageWriter.analyze();
        var preparation = inventoryWriter.hasInventoryForRoot(rootKey)
                ? prepareWarm(runId, overwriteExisting)
                : prepareCold(runId);
        LOGGER.info(
                "Đã prepare reconciliation: runId={}, source={}, changedFiles={}, durationMs={}",
                runId,
                preparation.source(),
                preparation.changedFiles(),
                elapsedMillis(startedNanos));
        return preparation;
    }

    private ScanReconciliationPreparation prepareCold(UUID runId) {
        return new ScanReconciliationPreparation(ScanReconciliationSource.COLD_STAGE, stageWriter.countRows(runId));
    }

    private ScanReconciliationPreparation prepareWarm(UUID runId, boolean overwriteExisting) {
        long changed = overwriteExisting ? stageWriter.materializeAll(runId) : stageWriter.materializeDiff(runId);
        return new ScanReconciliationPreparation(ScanReconciliationSource.WARM_DIFF, changed);
    }

    private void validateLease(UUID runId, String workerId) {
        ScanRunEntity run = runs.findById(runId).orElseThrow();
        if (!run.isLeaseActive(Instant.now()) || !workerId.equals(run.workerId())) {
            throw new ScanLeaseExpiredException(runId, workerId);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
