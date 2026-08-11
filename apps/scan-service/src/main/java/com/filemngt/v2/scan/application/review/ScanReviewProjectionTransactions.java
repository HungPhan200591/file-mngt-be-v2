package com.filemngt.v2.scan.application.review;

import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionTaskStore;
import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionTaskStore.Task;
import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionWriter;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Tách transaction worker khỏi scheduler để proxy Spring áp dụng claim/build/failure độc lập. */
public class ScanReviewProjectionTransactions {
    private final ScanReviewProjectionTaskStore tasks;
    private final ScanReviewProjectionWriter writer;

    public ScanReviewProjectionTransactions(ScanReviewProjectionTaskStore tasks, ScanReviewProjectionWriter writer) {
        this.tasks = tasks;
        this.writer = writer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Task> claim(String workerId, Instant now) {
        tasks.failExhausted(now);
        return tasks.claim(workerId, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rebuild(Task task, String workerId, Instant now) {
        writer.rebuild(task, workerId, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Task task, String workerId, Instant now, Throwable failure) {
        tasks.recordFailure(task, workerId, now, failure);
    }
}
