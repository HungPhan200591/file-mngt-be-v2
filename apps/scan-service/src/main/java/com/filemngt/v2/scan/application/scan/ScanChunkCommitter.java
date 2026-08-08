package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventorySetWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter.StageRowSource;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueCopyWriter;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalCopyWriter;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunProgressWriter;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunProgressWriter.Checkpoint;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunProgressWriter.Completion;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.adapter.out.persistence.timeout.ScanTransactionTimeouts;
import com.filemngt.v2.scan.application.exception.ScanLeaseExpiredException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
/** Commit chunk độc lập với timeout DB và conditional fence tại checkpoint/finalize. */
public class ScanChunkCommitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanChunkCommitter.class);

    private final ScanRunRepository runs;
    private final ScanProposalCopyWriter proposalCopyWriter;
    private final ScanIssueCopyWriter issueCopyWriter;
    private final ScanFileInventorySetWriter inventorySetWriter;
    private final ScanInventoryStageWriter stageWriter;
    private final ScanRunProgressWriter runProgressWriter;
    private final ScanTransactionTimeouts timeouts;

    public ScanChunkCommitter(
            ScanRunRepository runs,
            ScanProposalCopyWriter proposalCopyWriter,
            ScanIssueCopyWriter issueCopyWriter,
            ScanFileInventorySetWriter inventorySetWriter,
            ScanInventoryStageWriter stageWriter,
            ScanRunProgressWriter runProgressWriter,
            ScanTransactionTimeouts timeouts) {
        this.runs = runs;
        this.proposalCopyWriter = proposalCopyWriter;
        this.issueCopyWriter = issueCopyWriter;
        this.inventorySetWriter = inventorySetWriter;
        this.stageWriter = stageWriter;
        this.runProgressWriter = runProgressWriter;
        this.timeouts = timeouts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DiscoveryCommit commitDiscoverySegment(DiscoverySegment segment) {
        timeouts.applyMutationTimeout();
        validateLease(loadRun(segment.lease()), segment.lease());
        long copied = stageWriter.copySeen(segment.lease().runId(), segment.source());
        Instant leaseUntil = advanceDiscovery(segment, copied);
        logDiscoveryCommit(segment, copied, leaseUntil);
        return new DiscoveryCommit(copied, leaseUntil);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Instant commitChangedChunk(ChunkLease lease, ChunkBatch batch, ChunkProgress progress) {
        timeouts.applyMutationTimeout();
        validateLease(loadRun(lease), lease);
        inventorySetWriter.upsertChanged(lease.runId(), batch.firstPath(), batch.lastPath());
        commitProposalsChunk(batch.proposals());
        commitIssuesChunk(batch.issues());
        advanceCheckpoint(lease, batch.index(), progress);
        logChangedCommit(lease, batch.index(), progress);
        return lease.nextLeaseUntil();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long prepareReconciliation(UUID runId, String workerId) {
        long startedNanos = System.nanoTime();
        timeouts.applyMutationTimeout();
        var lease = new ChunkLease(runId, workerId, null);
        validateLease(loadRun(lease), lease);
        stageWriter.analyze();
        long changed = stageWriter.materializeDiff(runId);
        long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        LOGGER.info(
                "Đã materialize reconciliation diff: runId={}, changedFiles={}, durationMs={}",
                runId,
                changed,
                durationMillis);
        return changed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Instant heartbeatReconciliation(ReconciliationHeartbeat heartbeat) {
        timeouts.applyMutationTimeout();
        validateLease(loadRun(heartbeat.lease()), heartbeat.lease());
        advanceCheckpoint(heartbeat.lease(), heartbeat.index(), heartbeat.progress());
        return heartbeat.lease().nextLeaseUntil();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeRun(UUID runId, String workerId, String rootKey, ChunkProgress progress) {
        timeouts.applyMutationTimeout();
        var lease = new ChunkLease(runId, workerId, null);
        validateLease(loadRun(lease), lease);
        inventorySetWriter.markMissingFromStage(rootKey, runId);
        stageWriter.deleteRun(runId);
        completeRun(runId, workerId, progress);
        LOGGER.info(
                "Đã finalize scan runId={}: files={}, proposals={}, issues={}",
                runId,
                progress.files(),
                progress.proposals(),
                progress.issues());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupStage(UUID runId) {
        timeouts.applyMutationTimeout();
        stageWriter.deleteRun(runId);
    }

    private ScanRunEntity loadRun(ChunkLease lease) {
        return runs.findById(lease.runId()).orElseThrow();
    }

    private Instant advanceDiscovery(DiscoverySegment segment, long copied) {
        Instant checkpointAt = Instant.now();
        Instant leaseUntil = checkpointAt.plusSeconds(segment.leaseDurationSeconds());
        var checkpoint = new Checkpoint(
                segment.lease().runId(),
                segment.lease().workerId(),
                segment.index(),
                segment.previouslyScannedFiles() + copied,
                0,
                0,
                checkpointAt,
                leaseUntil);
        advanceCheckpoint(checkpoint, segment.lease());
        return leaseUntil;
    }

    private void advanceCheckpoint(ChunkLease lease, int chunkIndex, ChunkProgress progress) {
        Instant checkpointAt = Instant.now();
        var checkpoint = new Checkpoint(
                lease.runId(),
                lease.workerId(),
                chunkIndex,
                progress.files(),
                progress.proposals(),
                progress.issues(),
                checkpointAt,
                lease.nextLeaseUntil());
        advanceCheckpoint(checkpoint, lease);
    }

    private void advanceCheckpoint(Checkpoint checkpoint, ChunkLease lease) {
        if (!runProgressWriter.advanceCheckpoint(checkpoint)) {
            throw new ScanLeaseExpiredException(lease.runId(), lease.workerId());
        }
    }

    private void completeRun(UUID runId, String workerId, ChunkProgress progress) {
        Instant finishedAt = Instant.now();
        var completion = new Completion(
                runId, workerId, progress.files(), progress.proposals(), progress.issues(), finishedAt);
        if (!runProgressWriter.complete(completion)) {
            throw new ScanLeaseExpiredException(runId, workerId);
        }
    }

    private void validateLease(ScanRunEntity run, ChunkLease lease) {
        if (!run.isLeaseActive(Instant.now())
                || (lease.workerId() != null && !lease.workerId().equals(run.workerId()))) {
            LOGGER.error(
                    "Lease của worker không hợp lệ hoặc đã bị hủy: runId={}, workerId={}",
                    lease.runId(),
                    lease.workerId());
            throw new ScanLeaseExpiredException(lease.runId(), lease.workerId());
        }
    }

    private void commitProposalsChunk(List<ScanProposalEntity> proposalList) {
        if (!proposalList.isEmpty()) {
            proposalCopyWriter.copy(proposalList);
        }
    }

    private void commitIssuesChunk(List<ScanIssueEntity> issueList) {
        if (!issueList.isEmpty()) {
            issueCopyWriter.copy(issueList);
        }
    }

    private void logDiscoveryCommit(DiscoverySegment segment, long copied, Instant leaseUntil) {
        LOGGER.debug(
                "Đã commit discovery segment #{} cho runId={}: workerId={}, copied={}, nextLeaseUntil={}",
                segment.index(),
                segment.lease().runId(),
                segment.lease().workerId(),
                copied,
                leaseUntil);
    }

    private void logChangedCommit(ChunkLease lease, int chunkIndex, ChunkProgress progress) {
        LOGGER.debug(
                "Đã commit changed chunk #{} cho runId={}: workerId={}, proposals={}, issues={}, nextLeaseUntil={}",
                chunkIndex,
                lease.runId(),
                lease.workerId(),
                progress.proposals(),
                progress.issues(),
                lease.nextLeaseUntil());
    }

    public record ChunkLease(UUID runId, String workerId, Instant nextLeaseUntil) {}

    public record DiscoverySegment(
            ChunkLease lease,
            int index,
            long previouslyScannedFiles,
            long leaseDurationSeconds,
            StageRowSource source) {}

    public record DiscoveryCommit(long copied, Instant leaseUntil) {}

    public record ReconciliationHeartbeat(ChunkLease lease, int index, ChunkProgress progress) {}

    public record ChunkBatch(
            int index,
            String firstPath,
            String lastPath,
            List<ScanProposalEntity> proposals,
            List<ScanIssueEntity> issues) {}

    public record ChunkProgress(long files, long proposals, long issues) {}
}
