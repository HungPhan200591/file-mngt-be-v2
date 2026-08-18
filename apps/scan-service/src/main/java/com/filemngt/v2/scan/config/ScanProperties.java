package com.filemngt.v2.scan.config;

import com.filemngt.v2.scan.domain.scan.ScanProfile;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "scan")
@Validated
/** Binding cấu hình các filesystem root và tham số vận hành scan. */
public class ScanProperties {
    private List<Root> roots = new ArrayList<>();

    @Min(1)
    private long leaseDurationSeconds = 60;

    @Min(1)
    private long reconciliationStatementTimeoutSeconds = 30;

    @Min(1)
    private long mutationStatementTimeoutSeconds = 45;

    @Min(1)
    private long lockTimeoutSeconds = 5;

    @Min(1)
    private int businessChunkSize = 5_000;

    @Min(1)
    private int reconciliationParallelism = 8;

    private boolean reconciliationPipelineEnabled = true;

    @Min(1)
    private int reconciliationPipelineQueueCapacity = 1;

    private ReviewProjection reviewProjection = new ReviewProjection();

    private BulkDecision bulkDecision = new BulkDecision();

    private IssueRecheck issueRecheck = new IssueRecheck();

    public List<Root> getRoots() {
        return roots;
    }

    public void setRoots(List<Root> roots) {
        this.roots = roots == null ? List.of() : List.copyOf(roots);
    }

    public long getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    public long getReconciliationStatementTimeoutSeconds() {
        return reconciliationStatementTimeoutSeconds;
    }

    public void setReconciliationStatementTimeoutSeconds(long reconciliationStatementTimeoutSeconds) {
        this.reconciliationStatementTimeoutSeconds = reconciliationStatementTimeoutSeconds;
    }

    public long getMutationStatementTimeoutSeconds() {
        return mutationStatementTimeoutSeconds;
    }

    public void setMutationStatementTimeoutSeconds(long mutationStatementTimeoutSeconds) {
        this.mutationStatementTimeoutSeconds = mutationStatementTimeoutSeconds;
    }

    public long getLockTimeoutSeconds() {
        return lockTimeoutSeconds;
    }

    public void setLockTimeoutSeconds(long lockTimeoutSeconds) {
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    public int getBusinessChunkSize() {
        return businessChunkSize;
    }

    public void setBusinessChunkSize(int businessChunkSize) {
        this.businessChunkSize = businessChunkSize;
    }

    public int getReconciliationParallelism() {
        return reconciliationParallelism;
    }

    public void setReconciliationParallelism(int reconciliationParallelism) {
        this.reconciliationParallelism = reconciliationParallelism;
    }

    public boolean isReconciliationPipelineEnabled() {
        return reconciliationPipelineEnabled;
    }

    public void setReconciliationPipelineEnabled(boolean reconciliationPipelineEnabled) {
        this.reconciliationPipelineEnabled = reconciliationPipelineEnabled;
    }

    public int getReconciliationPipelineQueueCapacity() {
        return reconciliationPipelineQueueCapacity;
    }

    public void setReconciliationPipelineQueueCapacity(int reconciliationPipelineQueueCapacity) {
        this.reconciliationPipelineQueueCapacity = reconciliationPipelineQueueCapacity;
    }

    public ReviewProjection getReviewProjection() {
        return reviewProjection;
    }

    public void setReviewProjection(ReviewProjection reviewProjection) {
        this.reviewProjection = reviewProjection == null ? new ReviewProjection() : reviewProjection;
    }

    public BulkDecision getBulkDecision() {
        return bulkDecision;
    }

    public void setBulkDecision(BulkDecision bulkDecision) {
        this.bulkDecision = bulkDecision == null ? new BulkDecision() : bulkDecision;
    }

    public IssueRecheck getIssueRecheck() {
        return issueRecheck;
    }

    public void setIssueRecheck(IssueRecheck issueRecheck) {
        this.issueRecheck = issueRecheck == null ? new IssueRecheck() : issueRecheck;
    }

    public static class BulkDecision {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class IssueRecheck {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ReviewProjection {
        private boolean enabled = true;

        @Min(1)
        private long fixedDelayMs = 1_000;

        @Min(1)
        private long statementTimeoutSeconds = 45;

        @Min(1)
        private long leaseSeconds = 90;

        @Min(1)
        private int maxAttempts = 5;

        @Min(1)
        private long totalDeadlineSeconds = 1_800;

        @Min(1)
        private int decisionBatchSize = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long value) {
            fixedDelayMs = value;
        }

        public long getStatementTimeoutSeconds() {
            return statementTimeoutSeconds;
        }

        public void setStatementTimeoutSeconds(long value) {
            statementTimeoutSeconds = value;
        }

        public long getLeaseSeconds() {
            return leaseSeconds;
        }

        public void setLeaseSeconds(long value) {
            leaseSeconds = value;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int value) {
            maxAttempts = value;
        }

        public long getTotalDeadlineSeconds() {
            return totalDeadlineSeconds;
        }

        public void setTotalDeadlineSeconds(long value) {
            totalDeadlineSeconds = value;
        }

        public int getDecisionBatchSize() {
            return decisionBatchSize;
        }

        public void setDecisionBatchSize(int value) {
            decisionBatchSize = value;
        }
    }

    public record Root(String key, String path, ScanProfile profile) {}
}
