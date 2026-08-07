package com.filemngt.v2.scan.application.scan.deadline;

import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.adapter.out.persistence.timeout.ScanTransactionTimeouts;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
/** Chỉ database được quyền quyết định deadline nào thật sự làm run mất lease. */
public class ScanLeaseExpiryHandler {
    private static final String LEASE_EXPIRED_DETAIL = "Scan lease expired without durable progress";

    private final ScanRunRepository runs;
    private final ScanTransactionTimeouts timeouts;

    public ScanLeaseExpiryHandler(ScanRunRepository runs, ScanTransactionTimeouts timeouts) {
        this.runs = runs;
        this.timeouts = timeouts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(UUID runId, String workerId) {
        timeouts.applyMutationTimeout();
        return runs.failIfLeaseExpired(runId, workerId, LEASE_EXPIRED_DETAIL) == 1;
    }
}
