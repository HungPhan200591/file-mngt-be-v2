package com.filemngt.v2.scan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** Worker scan tách khỏi sender SSE và được Spring quản lý graceful shutdown. */
@Configuration
public class ScanTaskExecutionConfiguration {
    @Bean(name = "applicationTaskExecutor")
    SimpleAsyncTaskExecutor applicationTaskExecutor() {
        var executor = new SimpleAsyncTaskExecutor("scan-worker-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
