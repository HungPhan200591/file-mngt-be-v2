package com.filemngt.v2.scan.application.review;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scan.review-projection.enabled", havingValue = "true", matchIfMissing = true)
/** Poll một task mỗi nhịp; restart chỉ reclaim sau lease, không release lease chủ động khi shutdown. */
public class ScanReviewProjectionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanReviewProjectionWorker.class);

    private final ScanReviewProjectionTransactions transactions;
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
    private final String workerId = UUID.randomUUID().toString();

    public ScanReviewProjectionWorker(ScanReviewProjectionTransactions transactions) {
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${scan.review-projection.fixed-delay-ms:1000}")
    public void projectNextRoot() {
        if (!acceptingTasks.get()) return;
        var task = transactions.claim(workerId, Instant.now());
        if (task.isEmpty()) return;
        try {
            transactions.rebuild(task.get(), workerId, Instant.now());
            LOGGER.info(
                    "Completed review projection taskId={} runId={} generation={}",
                    task.get().id(),
                    task.get().scanRunId(),
                    task.get().generation());
        } catch (RuntimeException failure) {
            transactions.recordFailure(task.get(), workerId, Instant.now(), failure);
            LOGGER.warn(
                    "Review projection failed taskId={} runId={} attempt={} error={}",
                    task.get().id(),
                    task.get().scanRunId(),
                    task.get().attemptCount(),
                    failure.getMessage());
        }
    }

    @PreDestroy
    void stopAcceptingTasks() {
        acceptingTasks.set(false);
    }
}
