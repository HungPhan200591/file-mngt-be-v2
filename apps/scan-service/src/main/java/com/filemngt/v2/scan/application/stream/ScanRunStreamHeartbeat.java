package com.filemngt.v2.scan.application.stream;

import com.filemngt.v2.scan.config.ScanSseProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
/** Một heartbeat scheduler chung giữ stream idle sống mà không tạo timer theo connection. */
public class ScanRunStreamHeartbeat {
    private final ScheduledFuture<?> heartbeatTask;

    public ScanRunStreamHeartbeat(
            ScanRunStreamService streamService,
            ScanSseProperties properties,
            @Qualifier("scanSseHeartbeatScheduler") ScheduledExecutorService scheduler) {
        heartbeatTask = scheduler.scheduleAtFixedRate(
                streamService::heartbeat,
                properties.getHeartbeatSeconds(),
                properties.getHeartbeatSeconds(),
                TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        heartbeatTask.cancel(false);
    }
}
