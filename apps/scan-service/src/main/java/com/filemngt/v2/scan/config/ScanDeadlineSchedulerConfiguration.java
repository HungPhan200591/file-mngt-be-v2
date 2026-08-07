package com.filemngt.v2.scan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
/** Scheduler riêng cho deadline one-shot của scan run, tách khỏi HTTP và worker executor. */
public class ScanDeadlineSchedulerConfiguration {
    private static final int DEADLINE_SCHEDULER_POOL_SIZE = 2;

    @Bean("scanLeaseDeadlineScheduler")
    public TaskScheduler scanLeaseDeadlineScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(DEADLINE_SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("scan-lease-deadline-");
        return scheduler;
    }
}
