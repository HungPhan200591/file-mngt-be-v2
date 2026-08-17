package com.filemngt.v2.scan.application.approval;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationRepository;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Quyết định retry hay terminal failure sau lỗi chunk mà không dựa vào self-invocation proxy. */
public class ApprovalOperationStateService {
    private final ApprovalOperationRepository operations;
    private final ApprovalOperationProperties properties;

    public ApprovalOperationStateService(
            ApprovalOperationRepository operations, ApprovalOperationProperties properties) {
        this.operations = operations;
        this.properties = properties;
    }

    @Transactional
    public void retryOrFail(UUID operationId, String workerId, RuntimeException failure) {
        operations.lockById(operationId).ifPresent(operation -> {
            if (!operation.ownedBy(workerId)) return;
            String detail = failure.getClass().getSimpleName();
            boolean exhausted = operation.attemptCount() >= properties.getMaxAttempts();
            boolean expired = !operation
                    .acceptedAt()
                    .plusSeconds(properties.getTotalDeadlineSeconds())
                    .isAfter(Instant.now());
            if (exhausted || expired) operation.fail("APPROVAL_CHUNK_FAILED", detail);
            else operation.retry(detail);
        });
    }
}
