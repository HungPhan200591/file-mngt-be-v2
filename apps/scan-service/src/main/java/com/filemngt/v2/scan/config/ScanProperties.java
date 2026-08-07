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
    private int businessChunkSize = 15_000;

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

    public record Root(String key, String path, ScanProfile profile) {}
}
