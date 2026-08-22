package com.filemngt.v2.scan.application.approval;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationShardJdbcRepository;
import com.filemngt.v2.scan.application.outbox.OutboxPressureGate;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Claim/reclaim operation trong transaction ngắn bằng `SKIP LOCKED`. */
public class ApprovalOperationClaimService {
    private final ApprovalOperationProperties properties;
    private final ApprovalOperationShardJdbcRepository shards;
    private final OutboxPressureGate pressureGate;

    public ApprovalOperationClaimService(
            ApprovalOperationProperties properties,
            ApprovalOperationShardJdbcRepository shards,
            OutboxPressureGate pressureGate) {
        this.properties = properties;
        this.shards = shards;
        this.pressureGate = pressureGate;
    }

    @Transactional
    public Optional<ApprovalOperationClaim> claim(String workerId) {
        if (!pressureGate.allowBulkClaim()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        var shard = shards
                .claimNext(
                        workerId,
                        now,
                        properties.getLeaseSeconds(),
                        properties.getTotalDeadlineSeconds(),
                        properties.getMaxAttempts())
                .stream()
                .findFirst();
        if (shard.isEmpty()) {
            shards.finalizeReadyOperations();
            return Optional.empty();
        }
        return Optional.of(new ApprovalOperationClaim(
                shard.get().shardId(),
                shard.get().operationId(),
                shard.get().scanRunId(),
                shard.get().proposalCutoffId(),
                shard.get().shardNumber(),
                shard.get().shardCount(),
                shard.get().processingVersion(),
                shard.get().expectedRecordCount(),
                shard.get().committedRecordCount(),
                shard.get().sourceBatchCount(),
                shard.get().lastProposalId()));
    }
}
