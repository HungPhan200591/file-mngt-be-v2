package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationProposalJdbcRepository.ProposalRow;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository.DecisionWrite;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.application.approval.ApprovalOperationClaim;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.domain.candidate.ScanCandidateType;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;

/**
 * Chuẩn bị immutable decision/outbox payload ngoài transaction. Partition luôn bounded theo một approval chunk;
 * writer vẫn là thành phần duy nhất ghi database.
 */
@Component
public class ScanDecisionChunkPreparation {
    private final ScanOutboxEventFactory eventFactory;
    private final ScanFileInventoryRepository inventory;

    public ScanDecisionChunkPreparation(ScanOutboxEventFactory eventFactory, ScanFileInventoryRepository inventory) {
        this.eventFactory = eventFactory;
        this.inventory = inventory;
    }

    public PreparedChunk prepare(
            ApprovalOperationClaim claim,
            ScanRunEntity run,
            String batchId,
            List<ProposalRow> rows,
            Instant decidedAt,
            int preparationParallelism) {
        if (rows.isEmpty()) {
            return new PreparedChunk(List.of(), List.of());
        }
        if (preparationParallelism < 1) {
            throw new IllegalArgumentException("Approval preparation parallelism must be positive");
        }
        Set<String> missingDeletePaths = findMissingDeletePaths(run.rootKey(), rows);
        List<List<ProposalRow>> partitions = partition(rows, preparationParallelism);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<PreparedPart>> futures =
                    submit(executor, claim, run, batchId, partitions, decidedAt, missingDeletePaths);
            return merge(futures);
        }
    }

    private Set<String> findMissingDeletePaths(String rootKey, List<ProposalRow> rows) {
        List<String> deletePaths = rows.stream()
                .filter(this::isDeleteAsset)
                .map(ProposalRow::sourceRelativePath)
                .distinct()
                .toList();
        if (deletePaths.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(inventory.findPathsByRootKeyAndSourceRelativePathInAndState(
                rootKey, deletePaths, ScanFileInventoryState.MISSING));
    }

    private List<Future<PreparedPart>> submit(
            ExecutorService executor,
            ApprovalOperationClaim claim,
            ScanRunEntity run,
            String batchId,
            List<List<ProposalRow>> partitions,
            Instant decidedAt,
            Set<String> missingDeletePaths) {
        List<Future<PreparedPart>> futures = new ArrayList<>(partitions.size());
        for (List<ProposalRow> partition : partitions) {
            futures.add(executor.submit(
                    () -> preparePartition(claim, run, batchId, partition, decidedAt, missingDeletePaths)));
        }
        return futures;
    }

    private PreparedChunk merge(List<Future<PreparedPart>> futures) {
        List<DecisionWrite> writes = new ArrayList<>();
        List<ScanOutboxEventEntity> events = new ArrayList<>();
        try {
            for (Future<PreparedPart> future : futures) {
                PreparedPart part = future.get();
                writes.addAll(part.writes());
                events.addAll(part.events());
            }
            return new PreparedChunk(writes, events);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancel(futures);
            throw new IllegalStateException("Approval preparation bị gián đoạn", exception);
        } catch (ExecutionException exception) {
            cancel(futures);
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Approval preparation thất bại", cause);
        }
    }

    private PreparedPart preparePartition(
            ApprovalOperationClaim claim,
            ScanRunEntity run,
            String batchId,
            List<ProposalRow> rows,
            Instant decidedAt,
            Set<String> missingDeletePaths) {
        List<DecisionWrite> writes = new ArrayList<>(rows.size());
        List<ScanOutboxEventEntity> events = new ArrayList<>(rows.size());
        for (ProposalRow row : rows) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Approval preparation bị cancel");
            }
            UUID eventId = UuidV7.next();
            writes.add(new DecisionWrite(row.id(), eventId, decidedAt));
            events.add(eventFactory.createValidatedApproval(
                    eventId,
                    claim.scanRunId(),
                    row.toEntity(),
                    run,
                    claim.operationId(),
                    batchId,
                    !isDeleteAsset(row) || missingDeletePaths.contains(row.sourceRelativePath())));
        }
        return new PreparedPart(writes, events);
    }

    private void cancel(List<Future<PreparedPart>> futures) {
        for (Future<PreparedPart> future : futures) {
            future.cancel(true);
        }
    }

    private List<List<ProposalRow>> partition(List<ProposalRow> rows, int parallelism) {
        int partitionCount = Math.min(parallelism, rows.size());
        int baseSize = rows.size() / partitionCount;
        int remainder = rows.size() % partitionCount;
        List<List<ProposalRow>> partitions = new ArrayList<>(partitionCount);
        int offset = 0;
        for (int index = 0; index < partitionCount; index++) {
            int size = baseSize + (index < remainder ? 1 : 0);
            partitions.add(List.copyOf(rows.subList(offset, offset + size)));
            offset += size;
        }
        return partitions;
    }

    private boolean isDeleteAsset(ProposalRow row) {
        return ScanCandidateType.DELETE_ASSET.name().equals(row.candidateType());
    }

    public record PreparedChunk(List<DecisionWrite> writes, List<ScanOutboxEventEntity> events) {
        public PreparedChunk {
            writes = List.copyOf(writes);
            events = List.copyOf(events);
        }
    }

    private record PreparedPart(List<DecisionWrite> writes, List<ScanOutboxEventEntity> events) {}
}
