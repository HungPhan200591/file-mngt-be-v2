package com.filemngt.v2.scan.application.recheck;

import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.application.dto.ScanAsyncJobStatus;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueRecheckService {
    private final ScanIssueRepository issues;
    private final IssueRecheckJobRepository jobs;

    IssueRecheckService(ScanIssueRepository issues, IssueRecheckJobRepository jobs) {
        this.issues = issues;
        this.jobs = jobs;
    }

    @Transactional
    public UUID enqueue(UUID issueId) {
        if (!issues.existsById(issueId)) throw new IllegalArgumentException("Unknown scan issue: " + issueId);
        return jobs.save(new IssueRecheckJobEntity(UuidV7.next(), issueId)).id();
    }

    @Transactional(readOnly = true)
    public ScanAsyncJobStatus status(UUID jobId) {
        var job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown issue recheck job: " + jobId));
        return new ScanAsyncJobStatus(job.id(), "ISSUE_RECHECK", job.status(), null, job.createdAt(), job.startedAt(),
                job.finishedAt(), job.lastError(), job.observationScanRunId());
    }
}
