package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionDecisionStore;
import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionReadStore;
import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionReadStore.Candidate;
import com.filemngt.v2.scan.config.ScanProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
/** Gom policy projection cho decision để use case không phụ thuộc chi tiết watermark/JDBC. */
public class ScanReviewDecisionProjection {
    private final ScanReviewProjectionReadStore reads;
    private final ScanReviewProjectionDecisionStore writes;
    private final ScanProperties.ReviewProjection properties;

    public ScanReviewDecisionProjection(
            ScanReviewProjectionReadStore reads, ScanReviewProjectionDecisionStore writes, ScanProperties properties) {
        this.reads = reads;
        this.writes = writes;
        this.properties = properties.getReviewProjection();
    }

    public boolean canServe(String rootKey) {
        return properties.isEnabled() && reads.canServe(rootKey);
    }

    public List<Candidate> candidates(String state, String rootKey, String search) {
        return candidates(state, rootKey, search, null);
    }

    public List<Candidate> candidates(String state, String rootKey, String search, UUID scanRunId) {
        return reads.decisionCandidates(state, rootKey, search, scanRunId, properties.getDecisionBatchSize());
    }

    public void lock(String rootKey) {
        if (properties.isEnabled()) writes.lockRoot(rootKey);
    }

    public void apply(UUID proposalId, String rootKey, String decision, Instant decidedAt) {
        if (properties.isEnabled()) writes.apply(proposalId, rootKey, decision, decidedAt);
    }

    public void reopen(UUID proposalId, String rootKey) {
        if (properties.isEnabled()) writes.reopen(proposalId, rootKey);
    }
}
