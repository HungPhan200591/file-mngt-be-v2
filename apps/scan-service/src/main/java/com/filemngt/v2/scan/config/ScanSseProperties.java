package com.filemngt.v2.scan.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Giới hạn vận hành SSE; tách khỏi lease và không làm thay đổi scan state. */
@ConfigurationProperties(prefix = "scan.sse")
@Validated
public class ScanSseProperties {
    @Min(1)
    private long heartbeatSeconds = 15;

    @Min(1)
    private long connectionLifetimeSeconds = 300;

    @Min(1)
    private int maxConnectionsPerRun = 5;

    @Min(1)
    private int maxConnections = 100;

    @Min(1)
    private long progressIntervalMillis = 1000;

    public long getHeartbeatSeconds() {
        return heartbeatSeconds;
    }

    public void setHeartbeatSeconds(long heartbeatSeconds) {
        this.heartbeatSeconds = heartbeatSeconds;
    }

    public long getConnectionLifetimeSeconds() {
        return connectionLifetimeSeconds;
    }

    public void setConnectionLifetimeSeconds(long connectionLifetimeSeconds) {
        this.connectionLifetimeSeconds = connectionLifetimeSeconds;
    }

    public int getMaxConnectionsPerRun() {
        return maxConnectionsPerRun;
    }

    public void setMaxConnectionsPerRun(int maxConnectionsPerRun) {
        this.maxConnectionsPerRun = maxConnectionsPerRun;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public long getProgressIntervalMillis() {
        return progressIntervalMillis;
    }

    public void setProgressIntervalMillis(long progressIntervalMillis) {
        this.progressIntervalMillis = progressIntervalMillis;
    }
}
