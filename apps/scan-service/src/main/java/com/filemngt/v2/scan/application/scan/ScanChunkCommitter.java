package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryBatchWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter.StageRowSource;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunProgressWriter;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunProgressWriter.DiscoveryCheckpoint;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.exception.ScanLeaseExpiredException;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
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
 * Thực hiện commit chunk inventory/proposals/issues và gia hạn lease trong một transaction riêng biệt.
 * Đảm bảo tính bền vững (durability) theo chunk độc lập với các chunk khác.
 */
public class ScanChunkCommitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanChunkCommitter.class);

    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;
    private final ScanFileInventoryBatchWriter inventoryBatchWriter;
    private final ScanInventoryStageWriter stageWriter;
    private final ScanRunProgressWriter runProgressWriter;

    public ScanChunkCommitter(
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            ScanFileInventoryBatchWriter inventoryBatchWriter,
            ScanInventoryStageWriter stageWriter,
            ScanRunProgressWriter runProgressWriter) {
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.inventoryBatchWriter = inventoryBatchWriter;
        this.stageWriter = stageWriter;
        this.runProgressWriter = runProgressWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long commitDiscoverySegment(DiscoverySegment segment) {
        var lease = segment.lease();
        var run = runs.findById(lease.runId()).orElseThrow();
        validateLease(run, lease);

        long copied = stageWriter.copySeen(lease.runId(), segment.source());
        long scannedFiles = segment.previouslyScannedFiles() + copied;
        Instant nextLeaseUntil = fenceAndAdvanceDiscovery(segment, scannedFiles);
        LOGGER.debug(
                "Đã commit discovery segment #{} cho runId={}: workerId={}, copied={}, nextLeaseUntil={}",
                segment.index(),
                lease.runId(),
                lease.workerId(),
                copied,
                nextLeaseUntil);
        return copied;
    }

    private Instant fenceAndAdvanceDiscovery(DiscoverySegment segment, long scannedFiles) {
        var lease = segment.lease();
        Instant checkpointAt = Instant.now();
        Instant nextLeaseUntil = checkpointAt.plusSeconds(segment.leaseDurationSeconds());
        var checkpoint = new DiscoveryCheckpoint(
                lease.runId(),
                lease.workerId(),
                segment.index(),
                scannedFiles,
                checkpointAt,
                nextLeaseUntil);
        if (!runProgressWriter.advanceDiscovery(checkpoint)) {
            throw new ScanLeaseExpiredException(lease.runId(), lease.workerId());
        }
        return nextLeaseUntil;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitChangedChunk(ChunkLease lease, ChunkBatch batch, ChunkProgress progress) {
        var run = runs.findById(lease.runId()).orElseThrow();
        validateLease(run, lease);

        inventoryBatchWriter.upsertChanged(batch.changedInventoryItems());
        commitProposalsChunk(batch.proposals());
        commitIssuesChunk(batch.issues());

        run.updateCheckpoint(
                batch.index(), progress.files(), progress.proposals(), progress.issues(), lease.nextLeaseUntil());
        runs.saveAndFlush(run);
        LOGGER.debug(
                "Đã commit changed chunk #{} cho runId={}: workerId={}, proposals={}, issues={}, nextLeaseUntil={}",
                batch.index(),
                lease.runId(),
                lease.workerId(),
                progress.proposals(),
                progress.issues(),
                lease.nextLeaseUntil());
    }

    /**
     * Hoàn tất scan run trong một transaction độc lập được bảo vệ bởi lease validation.
     * Đảm bảo markMissing và complete chạy nguyên tử, chỉ khi worker vẫn làm chủ run.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeRun(UUID runId, String workerId, String rootKey, ChunkProgress finalProgress) {
        var run = runs.findById(runId).orElseThrow();
        validateLease(run, new ChunkLease(runId, workerId, null));

        inventoryBatchWriter.markMissingFromStage(rootKey, runId);
        stageWriter.deleteRun(runId);
        run.complete(finalProgress.files(), finalProgress.proposals(), finalProgress.issues());
        runs.saveAndFlush(run);
        LOGGER.info(
                "Đã finalize scan runId={}: files={}, proposals={}, issues={}",
                runId,
                finalProgress.files(),
                finalProgress.proposals(),
                finalProgress.issues());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupStage(UUID runId) {
        stageWriter.deleteRun(runId);
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
            proposals.saveAll(proposalList);
            proposals.flush();
        }
    }

    private void commitIssuesChunk(List<ScanIssueEntity> issueList) {
        if (!issueList.isEmpty()) {
            issues.saveAll(issueList);
            issues.flush();
        }
    }

    public record ChunkLease(UUID runId, String workerId, Instant nextLeaseUntil) {}

    public record DiscoverySegment(
            ChunkLease lease,
            int index,
            long previouslyScannedFiles,
            long leaseDurationSeconds,
            StageRowSource source) {}

    public record ChunkBatch(
            int index,
            List<ScanInventoryItem> changedInventoryItems,
            List<ScanProposalEntity> proposals,
            List<ScanIssueEntity> issues) {}

    public record ChunkProgress(long files, long proposals, long issues) {}
}
