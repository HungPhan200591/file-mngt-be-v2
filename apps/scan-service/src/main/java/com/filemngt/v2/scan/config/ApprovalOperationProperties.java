package com.filemngt.v2.scan.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "scan.approval-operation")
@Validated
/** Giới hạn vận hành riêng cho durable approval operation; không dùng lại lease của scan preview. */
public class ApprovalOperationProperties {
    private boolean enabled = true;

    @Min(1)
    private long fixedDelayMs = 250;

    @Min(1)
    private int chunkSize = 25_000;

    @Min(1)
    private int jdbcBatchSize = 500;

    private boolean copyEnabled = true;

    @Min(1)
    private int preparationParallelism = 4;

    @Min(1)
    @Max(256)
    private int completionShardCount = 64;

    @Min(1)
    private int workerConcurrency = 4;

    @Min(1)
    private long leaseSeconds = 30;

    @Min(1)
    private int maxAttempts = 5;

    @Min(1)
    private long totalDeadlineSeconds = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getJdbcBatchSize() {
        return jdbcBatchSize;
    }

    public void setJdbcBatchSize(int jdbcBatchSize) {
        this.jdbcBatchSize = jdbcBatchSize;
    }

    public boolean isCopyEnabled() {
        return copyEnabled;
    }

    public void setCopyEnabled(boolean copyEnabled) {
        this.copyEnabled = copyEnabled;
    }

    public int getPreparationParallelism() {
        return preparationParallelism;
    }

    public int getCompletionShardCount() {
        return completionShardCount;
    }

    public void setCompletionShardCount(int completionShardCount) {
        this.completionShardCount = completionShardCount;
    }

    public int getWorkerConcurrency() {
        return workerConcurrency;
    }

    public void setWorkerConcurrency(int workerConcurrency) {
        this.workerConcurrency = workerConcurrency;
    }

    /** Giữ biến môi trường shard-count cũ như alias của worker concurrency trong rollout. */
    @Deprecated(forRemoval = true)
    public int getShardCount() {
        return workerConcurrency;
    }

    @Deprecated(forRemoval = true)
    public void setShardCount(int shardCount) {
        workerConcurrency = shardCount;
    }

    public void setPreparationParallelism(int preparationParallelism) {
        this.preparationParallelism = preparationParallelism;
    }

    public long getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(long leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getTotalDeadlineSeconds() {
        return totalDeadlineSeconds;
    }

    public void setTotalDeadlineSeconds(long totalDeadlineSeconds) {
        this.totalDeadlineSeconds = totalDeadlineSeconds;
    }
}
