package com.filemngt.v2.scan.application.scan.reconciliation;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryDiffReader;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryDiffReader.ChangedPage;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageReader;
import com.filemngt.v2.scan.adapter.out.persistence.timeout.ScanTransactionTimeouts;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
/** Đọc một reconciliation page trong transaction riêng để PostgreSQL timeout chỉ áp dụng cho page đó. */
public class ScanReconciliationPageReader {
    private final ScanInventoryDiffReader diffReader;
    private final ScanInventoryStageReader stageReader;
    private final ScanTransactionTimeouts timeouts;

    public ScanReconciliationPageReader(
            ScanInventoryDiffReader diffReader, ScanInventoryStageReader stageReader, ScanTransactionTimeouts timeouts) {
        this.diffReader = diffReader;
        this.stageReader = stageReader;
        this.timeouts = timeouts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ChangedPage findPage(
            ScanReconciliationSource source, UUID runId, String rootKey, String afterPath, int limit) {
        timeouts.applyReconciliationTimeout();
        return switch (source) {
            case COLD_STAGE -> stageReader.findPage(runId, rootKey, afterPath, limit);
            case WARM_DIFF -> diffReader.findChangedPage(runId, afterPath, limit);
        };
    }
}
