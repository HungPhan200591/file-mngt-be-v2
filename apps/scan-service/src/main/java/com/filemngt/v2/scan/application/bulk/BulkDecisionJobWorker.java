package com.filemngt.v2.scan.application.bulk;

import com.filemngt.v2.scan.application.decision.ScanReviewQueueDecisionBatch;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class BulkDecisionJobWorker {
    private final BulkDecisionJobRepository jobs;
    private final BulkDecisionClaimService claims;
    private final ScanReviewQueueDecisionBatch batches;
    private final String workerId = "bulk-decision-" + UuidV7.next();

    BulkDecisionJobWorker(BulkDecisionJobRepository jobs, BulkDecisionClaimService claims, ScanReviewQueueDecisionBatch batches) { this.jobs = jobs; this.claims = claims; this.batches = batches; }

    @Scheduled(fixedDelayString = "${scan.bulk-decision.fixed-delay-ms:1000}")
    void processNext() {
        claims.claim(workerId).ifPresent(this::process);
    }

    private void process(BulkDecisionJobEntity job) {
        job.claim(workerId);
        try {
            int count = job.decision().equals("REOPEN")
                    ? batches.reopen(job.rootKey(), job.search(), job.scanRunId())
                    : batches.decide(job.rootKey(), job.search(), job.scanRunId(), job.decision());
            if (count == 0) complete(job.id()); else progress(job.id(), count);
        } catch (RuntimeException exception) { fail(job.id(), exception.getMessage()); }
    }

    @Transactional
    void progress(java.util.UUID id, int count) { jobs.findById(id).ifPresent(job -> { job.progress(count); jobs.save(job); }); }

    @Transactional
    void complete(java.util.UUID id) { jobs.findById(id).ifPresent(job -> { job.complete(); jobs.save(job); }); }

    @Transactional
    void fail(java.util.UUID id, String detail) { jobs.findById(id).ifPresent(job -> { job.fail(detail); jobs.save(job); }); }
}
