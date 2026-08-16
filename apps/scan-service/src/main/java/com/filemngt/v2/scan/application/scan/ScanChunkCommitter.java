package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventorySetWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter.StageRowSource;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueCopyWriter;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalCopyWriter;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunProgressWriter;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunProgressWriter.Checkpoint;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunProgressWriter.Completion;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.adapter.out.persistence.timeout.ScanTransactionTimeouts;
import com.filemngt.v2.scan.application.exception.ScanLeaseExpiredException;
import com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
/**
 * Commit chunk độc lập với timeout DB và conditional fence tại checkpoint/finalize.
 * Giữ các primitive commit trong cùng type vì chúng phải dùng chung lease, timeout và
 * transaction boundary; writer riêng chịu trách nhiệm SQL/COPY.
 */
public class ScanChunkCommitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanChunkCommitter.class);

    private final ScanRunRepository runs;
    private final ScanProposalCopyWriter proposalCopyWriter;
    private final ScanIssueCopyWriter issueCopyWriter;
    private final ScanFileInventorySetWriter inventorySetWriter;
    private final ScanInventoryStageWriter stageWriter;
    private final ScanRunProgressWriter runProgressWriter;
    private final ScanTransactionTimeouts timeouts;
    private final ScanChunkCommitTelemetry commitTelemetry = new ScanChunkCommitTelemetry();

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
    public Instant commitChangedChunk(
            ChunkLease lease, ChunkBatch batch, ChunkProgress progress, ScanExecutionTimeline timeline) {
        var telemetry = commitTelemetry.begin(timeline, lease, batch.index());
        timeouts.applyMutationTimeout();
        validateLease(loadRun(lease), lease);
        telemetry.inventoryWritten(writeInventory(lease, batch));
        telemetry.proposalsCopied(copyProposals(batch.proposals()));
        telemetry.issuesCopied(copyIssues(batch.issues()));
        long checkpointStartedNanos = System.nanoTime();
        advanceCheckpoint(lease, batch.index(), progress);
        telemetry.checkpointWritten(elapsedMillis(checkpointStartedNanos));
        return lease.nextLeaseUntil();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Instant heartbeatReconciliation(ReconciliationHeartbeat heartbeat) {
        timeouts.applyMutationTimeout();
        validateLease(loadRun(heartbeat.lease()), heartbeat.lease());
        advanceCheckpoint(heartbeat.lease(), heartbeat.index(), heartbeat.progress());
        return heartbeat.lease().nextLeaseUntil();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChunkProgress finalizeRun(
            UUID runId, String workerId, String rootKey, boolean overwriteExisting, ChunkProgress progress) {
        timeouts.applyMutationTimeout();
        var lease = new ChunkLease(runId, workerId, null);
        validateLease(loadRun(lease), lease);
        int removalProposals = overwriteExisting ? inventorySetWriter.insertRemovalProposals(rootKey, runId) : 0;
        if (overwriteExisting) inventorySetWriter.markMissingFromStage(rootKey, runId);
        stageWriter.deleteRun(runId);
        var completedProgress = new ChunkProgress(
                progress.files(),
                progress.proposals() + removalProposals,
                progress.issues(),
                progress.changedFiles(),
                progress.reconciledFiles());
        completeRun(runId, workerId, rootKey, completedProgress);
        LOGGER.info(
                "Đã finalize scan runId={}: files={}, proposals={}, issues={}",
                runId,
                progress.files(),
                completedProgress.proposals(),
                completedProgress.issues());
        return completedProgress;
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
                null,
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
                progress.changedFiles(),
                progress.reconciledFiles(),
                checkpointAt,
                lease.nextLeaseUntil());
        advanceCheckpoint(checkpoint, lease);
    }

    private void advanceCheckpoint(Checkpoint checkpoint, ChunkLease lease) {
        if (!runProgressWriter.advanceCheckpoint(checkpoint)) {
            throw new ScanLeaseExpiredException(lease.runId(), lease.workerId());
        }
    }

    private void completeRun(UUID runId, String workerId, String rootKey, ChunkProgress progress) {
        Instant finishedAt = Instant.now();
        var completion = new Completion(
                runId,
                workerId,
                rootKey,
                progress.files(),
                progress.proposals(),
                progress.issues(),
                progress.changedFiles(),
                progress.reconciledFiles(),
                finishedAt);
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

    private long writeInventory(ChunkLease lease, ChunkBatch batch) {
        long startedNanos = System.nanoTime();
        switch (batch.source()) {
            case COLD_STAGE -> inventorySetWriter.insertCold(lease.runId(), batch.firstPath(), batch.lastPath());
            case WARM_DIFF -> inventorySetWriter.upsertChanged(lease.runId(), batch.firstPath(), batch.lastPath());
        }
        return elapsedMillis(startedNanos);
    }

    private long copyProposals(List<ScanProposalEntity> proposals) {
        long startedNanos = System.nanoTime();
        if (!proposals.isEmpty()) {
            proposalCopyWriter.copy(proposals);
        }
        return elapsedMillis(startedNanos);
    }

    private long copyIssues(List<ScanIssueEntity> issues) {
        long startedNanos = System.nanoTime();
        if (!issues.isEmpty()) {
            issueCopyWriter.copy(issues);
        }
        return elapsedMillis(startedNanos);
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
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
            ScanReconciliationSource source,
            List<ScanProposalEntity> proposals,
            List<ScanIssueEntity> issues) {}

    public record ChunkProgress(long files, long proposals, long issues, Long changedFiles, long reconciledFiles) {}
}
