package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryBatchWriter;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.exception.ScanLeaseExpiredException;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
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

    public ScanChunkCommitter(
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            ScanFileInventoryBatchWriter inventoryBatchWriter) {
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.inventoryBatchWriter = inventoryBatchWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitChunk(ChunkLease lease, ChunkBatch batch, ChunkProgress progress) {
        var run = runs.findById(lease.runId()).orElseThrow();
        validateLease(run, lease);

        inventoryBatchWriter.upsertPresent(batch.inventoryItems(), lease.runId());
        commitProposalsChunk(batch.proposals());
        commitIssuesChunk(batch.issues());

        run.updateCheckpoint(
                batch.index(), progress.files(), progress.proposals(), progress.issues(), lease.nextLeaseUntil());
        runs.saveAndFlush(run);
        LOGGER.debug(
                "Đã commit chunk #{} cho runId={}: workerId={}, proposals={}, issues={}, nextLeaseUntil={}",
                batch.index(),
                lease.runId(),
                lease.workerId(),
                progress.proposals(),
                progress.issues(),
                lease.nextLeaseUntil());
    }

    private void validateLease(ScanRunEntity run, ChunkLease lease) {
        if (run.status() != ScanRunStatus.RUNNING
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

    public record ChunkBatch(
            int index,
            List<ScanInventoryItem> inventoryItems,
            List<ScanProposalEntity> proposals,
            List<ScanIssueEntity> issues) {}

    public record ChunkProgress(long files, long proposals, long issues) {}
}
