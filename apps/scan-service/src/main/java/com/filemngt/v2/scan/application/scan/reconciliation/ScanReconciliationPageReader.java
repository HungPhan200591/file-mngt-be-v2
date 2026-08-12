package com.filemngt.v2.scan.application.scan.reconciliation;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryDiffReader;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryDiffReader.ChangedPage;
import com.filemngt.v2.scan.adapter.out.persistence.timeout.ScanTransactionTimeouts;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
/** Đọc một reconciliation page trong transaction riêng để PostgreSQL timeout chỉ áp dụng cho page đó. */
public class ScanReconciliationPageReader {
    private final ScanInventoryDiffReader diffReader;
    private final ScanTransactionTimeouts timeouts;

    public ScanReconciliationPageReader(ScanInventoryDiffReader diffReader, ScanTransactionTimeouts timeouts) {
        this.diffReader = diffReader;
        this.timeouts = timeouts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ChangedPage findChangedPage(UUID runId, String afterPath, int limit) {
        timeouts.applyReconciliationTimeout();
        return diffReader.findChangedPage(runId, afterPath, limit);
    }
}
