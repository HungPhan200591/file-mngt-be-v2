package com.filemngt.v2.scan.application.bulk;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BulkDecisionClaimService {
    private final BulkDecisionJobRepository jobs;

    BulkDecisionClaimService(BulkDecisionJobRepository jobs) { this.jobs = jobs; }

    @Transactional
    Optional<BulkDecisionJobEntity> claim(String workerId) {
        var job = jobs.lockNext(Instant.now()).stream().findFirst();
        job.ifPresent(value -> { value.claim(workerId); jobs.save(value); });
        return job;
    }
}
