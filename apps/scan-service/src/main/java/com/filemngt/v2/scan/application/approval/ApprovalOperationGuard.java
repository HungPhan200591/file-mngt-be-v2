package com.filemngt.v2.scan.application.approval;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationRepository;
import com.filemngt.v2.scan.application.exception.ApprovalOperationConflictException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
/** Chặn mutation cạnh tranh làm sai expected cardinality của approval operation active. */
public class ApprovalOperationGuard {
    private static final List<String> ACTIVE = List.of("ACCEPTED", "RUNNING");

    private final ApprovalOperationRepository operations;

    public ApprovalOperationGuard(ApprovalOperationRepository operations) {
        this.operations = operations;
    }

    public void ensureInactive(UUID scanRunId) {
        if (operations.existsByScanRunIdAndStatusIn(scanRunId, ACTIVE)) {
            throw new ApprovalOperationConflictException(scanRunId);
        }
    }

    public void ensureInactive(Collection<UUID> scanRunIds) {
        if (scanRunIds.isEmpty()) return;
        var active = operations.findActiveScanRunIds(scanRunIds);
        if (!active.isEmpty()) throw new ApprovalOperationConflictException(active.getFirst());
    }
}
