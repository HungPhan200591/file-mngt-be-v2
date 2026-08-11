package com.filemngt.v2.scan.application.recheck;

import com.filemngt.v2.scan.domain.identity.UuidV7;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class IssueRecheckWorker {
    private final IssueRecheckClaimService claims;
    private final IssueRecheckObservationResolver resolver;
    private final IssueRecheckPersistence persistence;
    private final String workerId = "issue-recheck-" + UuidV7.next();

    IssueRecheckWorker(IssueRecheckClaimService claims, IssueRecheckObservationResolver resolver, IssueRecheckPersistence persistence) {
        this.claims = claims;
        this.resolver = resolver;
        this.persistence = persistence;
    }

    @Scheduled(fixedDelayString = "${scan.issue-recheck.fixed-delay-ms:1000}")
    void processNext() {
        claims.claim(workerId).ifPresent(this::process);
    }

    private void process(IssueRecheckJobEntity job) {
        try {
            persistence.persist(job, resolver.resolve(job.issueId()));
        } catch (RuntimeException exception) {
            persistence.fail(job.id(), exception);
        }
    }
}
