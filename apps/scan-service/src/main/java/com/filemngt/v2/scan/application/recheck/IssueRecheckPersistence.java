package com.filemngt.v2.scan.application.recheck;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryEntity;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryRepository;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionTaskStore;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IssueRecheckPersistence {
    private final IssueRecheckJobRepository jobs;
    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;
    private final ScanFileInventoryRepository inventory;
    private final ScanReviewProjectionTaskStore projectionTasks;

    IssueRecheckPersistence(
            IssueRecheckJobRepository jobs,
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            ScanFileInventoryRepository inventory,
            ScanReviewProjectionTaskStore projectionTasks) {
        this.jobs = jobs;
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.inventory = inventory;
        this.projectionTasks = projectionTasks;
    }

    @Transactional
    void persist(IssueRecheckJobEntity job, IssueRecheckObservationResolver.Observation observation) {
        UUID runId = UuidV7.next();
        var run = new ScanRunEntity(
                runId, observation.root().key(), observation.root().profile(), Instant.now(), null);
        run.complete(
                1,
                observation.result() instanceof com.filemngt.v2.scan.application.scan.ScanFileAnalyzer.Proposal ? 1 : 0,
                observation.result() instanceof com.filemngt.v2.scan.application.scan.ScanFileAnalyzer.Issue ? 1 : 0);
        runs.save(run);
        var state = observation.present() ? ScanFileInventoryState.PRESENT : ScanFileInventoryState.MISSING;
        inventory
                .findByRootKeyAndSourceRelativePath(
                        observation.root().key(), observation.issue().sourceRelativePath())
                .ifPresentOrElse(
                        item -> item.updateMetadata(observation.size(), observation.modifiedAt(), state),
                        () -> inventory.save(new ScanFileInventoryEntity(
                                UuidV7.next(),
                                observation.root().key(),
                                observation.issue().sourceRelativePath(),
                                observation.size(),
                                observation.modifiedAt(),
                                state)));
        switch (observation.result()) {
            case com.filemngt.v2.scan.application.scan.ScanFileAnalyzer.Proposal(var proposal) ->
                proposals.save(copy(proposal, runId));
            case com.filemngt.v2.scan.application.scan.ScanFileAnalyzer.Issue(var issue) ->
                issues.save(new ScanIssueEntity(
                        issue.id(), runId, issue.sourceRelativePath(), issue.code(), issue.detail()));
        }
        projectionTasks.enqueue(runId, observation.root().key());
        job.complete(runId);
        jobs.save(job);
    }

    @Transactional
    void fail(UUID jobId, RuntimeException exception) {
        jobs.findById(jobId).ifPresent(job -> job.fail(exception.getMessage()));
    }

    private ScanProposalEntity copy(ScanProposalEntity proposal, UUID runId) {
        return new ScanProposalEntity(
                proposal.id(),
                runId,
                proposal.sourceRelativePath(),
                proposal.profile(),
                proposal.candidateType(),
                proposal.identityKey(),
                proposal.displayTitle(),
                proposal.assetRole(),
                proposal.evidence());
    }
}
