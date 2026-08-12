package com.filemngt.v2.scan.application.bulk;

import com.filemngt.v2.scan.application.dto.ScanAsyncJobStatus;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BulkDecisionJobService {
    private final BulkDecisionJobRepository jobs;

    BulkDecisionJobService(BulkDecisionJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public UUID enqueue(String rootKey, String search, UUID scanRunId, String decision) {
        if (!decision.equals("APPROVE") && !decision.equals("REJECT") && !decision.equals("REOPEN")) {
            throw new IllegalArgumentException("Unsupported bulk decision: " + decision);
        }
        return jobs.save(new BulkDecisionJobEntity(UuidV7.next(), rootKey, search, scanRunId, decision))
                .id();
    }

    @Transactional(readOnly = true)
    public ScanAsyncJobStatus status(UUID jobId) {
        var job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown bulk decision job: " + jobId));
        return new ScanAsyncJobStatus(
                job.id(),
                "BULK_DECISION",
                job.status(),
                job.processedCount(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                job.lastError(),
                null);
    }
}
