package com.filemngt.v2.catalog.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "catalog.outbox.operation-relay")
public class CatalogOutboxRelayProperties {
    @Min(64)
    @Max(64)
    private int laneCount = 64;

    @Min(1)
    private int workerCount = 4;

    @Min(1)
    private int fetchSize = 2_000;

    @Min(1)
    private long leaseSeconds = 30;

    @Min(1)
    private long idleBackoffMillis = 5;

    @Min(1)
    private long maximumFailureBackoffMillis = 5_000;

    @NotBlank
    private String instanceId = "catalog-operation-relay";

    public int getLaneCount() {
        return laneCount;
    }

    public void setLaneCount(int laneCount) {
        this.laneCount = laneCount;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public long getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(long leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public long getIdleBackoffMillis() {
        return idleBackoffMillis;
    }

    public void setIdleBackoffMillis(long idleBackoffMillis) {
        this.idleBackoffMillis = idleBackoffMillis;
    }

    public long getMaximumFailureBackoffMillis() {
        return maximumFailureBackoffMillis;
    }

    public void setMaximumFailureBackoffMillis(long maximumFailureBackoffMillis) {
        this.maximumFailureBackoffMillis = maximumFailureBackoffMillis;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
