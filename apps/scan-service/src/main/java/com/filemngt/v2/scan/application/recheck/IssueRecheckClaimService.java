package com.filemngt.v2.scan.application.recheck;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IssueRecheckClaimService {
    private static final long LEASE_SECONDS = 60;
    private final IssueRecheckJobRepository jobs;

    IssueRecheckClaimService(IssueRecheckJobRepository jobs) { this.jobs = jobs; }

    @Transactional
    Optional<IssueRecheckJobEntity> claim(String workerId) {
        var claimed = jobs.lockNext(Instant.now()).stream().findFirst();
        claimed.ifPresent(job -> job.claim(workerId, Instant.now().plusSeconds(LEASE_SECONDS)));
        return claimed;
    }
}
