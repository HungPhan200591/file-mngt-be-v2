package com.filemngt.v2.scan.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/** Executor riêng giúp client SSE chậm không chiếm worker quét filesystem. */
public class ScanSseConfiguration {
    @Bean(name = "scanSseHeartbeatScheduler", destroyMethod = "shutdown")
    ScheduledExecutorService scanSseHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("scan-sse-heartbeat-", 0).factory());
    }

    @Bean(name = "scanSseSenderExecutor", destroyMethod = "shutdown")
    ExecutorService scanSseSenderExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
