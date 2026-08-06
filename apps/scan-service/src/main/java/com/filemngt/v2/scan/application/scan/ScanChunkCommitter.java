package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.exception.ScanLeaseExpiredException;
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
 * Thực hiện commit chunk proposals/issues và gia hạn lease trong một transaction riêng biệt.
 * Đảm bảo tính bền vững (durability) theo chunk độc lập với các chunk khác.
 */
public class ScanChunkCommitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanChunkCommitter.class);

    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;

    public ScanChunkCommitter(ScanRunRepository runs, ScanProposalRepository proposals, ScanIssueRepository issues) {
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitChunk(ChunkLease lease, ChunkBatch batch, ChunkProgress progress) {
        var run = runs.findById(lease.runId()).orElseThrow();
        if (run.status() != ScanRunStatus.RUNNING
                || (lease.workerId() != null && !lease.workerId().equals(run.workerId()))) {
            LOGGER.error(
                    "Lease của worker không hợp lệ hoặc đã bị hủy: runId={}, workerId={}",
                    lease.runId(),
                    lease.workerId());
            throw new ScanLeaseExpiredException(lease.runId(), lease.workerId());
        }

        if (!batch.proposals().isEmpty()) {
            proposals.saveAll(batch.proposals());
            proposals.flush();
            batch.proposals().clear();
        }

        if (!batch.issues().isEmpty()) {
            issues.saveAll(batch.issues());
            issues.flush();
            batch.issues().clear();
        }

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

    public record ChunkLease(UUID runId, String workerId, Instant nextLeaseUntil) {}

    public record ChunkBatch(int index, List<ScanProposalEntity> proposals, List<ScanIssueEntity> issues) {}

    public record ChunkProgress(long files, long proposals, long issues) {}
}
