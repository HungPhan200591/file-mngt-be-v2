package com.filemngt.v2.scan.application.approval;

import com.filemngt.v2.scan.application.decision.ScanRunDecisionBatch;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scan.approval-operation.enabled", havingValue = "true", matchIfMissing = true)
/** Worker durable; shutdown không release lease, restart reclaim sau expiry. */
public class ApprovalOperationWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalOperationWorker.class);

    private final ApprovalOperationClaimService claims;
    private final ApprovalOperationStateService states;
    private final ScanRunDecisionBatch batches;
    private final ApprovalOperationProperties properties;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Adaptive backoff: khi tất cả shards đều idle (chưa có operation hoặc đang xử lý),
     * tăng dần khoảng nghỉ để tránh spam empty SELECT ... FOR UPDATE SKIP LOCKED vào DB.
     * Reset ngay khi có shard claim thành công.
     */
    private static final long BACKOFF_MAX_MS = 2000;

    private static final long BACKOFF_STEP_MS = 250;

    private long currentBackoffMs = 0;

    public ApprovalOperationWorker(
            ApprovalOperationClaimService claims,
            ApprovalOperationStateService states,
            ScanRunDecisionBatch batches,
            ApprovalOperationProperties properties) {
        this.claims = claims;
        this.states = states;
        this.batches = batches;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${scan.approval-operation.fixed-delay-ms:250}")
    public void processNext() {
        if (currentBackoffMs > 0) {
            try {
                Thread.sleep(currentBackoffMs);
            } catch (InterruptedException interrupt) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        var anyClaimedRef = new AtomicBoolean(false);
        var futures = new ArrayList<Future<?>>();
        for (int index = 0; index < properties.getWorkerConcurrency(); index++) {
            futures.add(workers.submit(() -> {
                String workerId = "approval-" + UuidV7.next();
                claims.claim(workerId).ifPresent(claim -> {
                    anyClaimedRef.set(true);
                    process(claim, workerId);
                });
            }));
        }
        for (var future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
                // Lỗi chi tiết đã được log trong process()
            }
        }

        if (anyClaimedRef.get()) {
            currentBackoffMs = 0;
        } else {
            currentBackoffMs = Math.min(currentBackoffMs + BACKOFF_STEP_MS, BACKOFF_MAX_MS);
        }
    }

    @PreDestroy
    void shutdown() {
        workers.close();
    }

    private void process(ApprovalOperationClaim claim, String workerId) {
        try {
            batches.process(claim, workerId);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Approval operation thất bại tạm thời: operationId={}, scanRunId={}, failure={}",
                    claim.operationId(),
                    claim.scanRunId(),
                    failure.getClass().getSimpleName());
            states.retryOrFail(claim, workerId, failure);
        }
    }
}
