package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.scan.config.OutboxDrainProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
/** Hard bound cho event đã claim nhưng chưa conditional-mark; callback Kafka không truy cập database. */
public class OutboxInFlightWindow {
    private final int maximum;
    private final AtomicInteger occupied = new AtomicInteger();
    private final ArrayBlockingQueue<OutboxCompletion> completions;

    public OutboxInFlightWindow(OutboxDrainProperties properties) {
        maximum = properties.getMaxInFlightEvents();
        completions = new ArrayBlockingQueue<>(maximum);
    }

    public int freeSlots() {
        return maximum - occupied.get();
    }

    public int occupied() {
        return occupied.get();
    }

    public int maximum() {
        return maximum;
    }

    public int completionDepth() {
        return completions.size();
    }

    public void reserve(int count) {
        if (count < 0 || count > freeSlots()) {
            throw new IllegalArgumentException("Outbox in-flight window vượt quá giới hạn");
        }
        occupied.addAndGet(count);
    }

    public void complete(OutboxCompletion completion) {
        if (!completions.offer(completion)) {
            throw new IllegalStateException("Outbox completion queue vượt quá in-flight window");
        }
    }

    public List<OutboxCompletion> drain(int maximumCount) {
        var drained = new ArrayList<OutboxCompletion>(maximumCount);
        completions.drainTo(drained, maximumCount);
        return List.copyOf(drained);
    }

    public boolean awaitCompletion(Duration timeout) throws InterruptedException {
        OutboxCompletion completion = completions.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (completion == null) {
            return false;
        }
        if (!completions.offer(completion)) {
            throw new IllegalStateException("Outbox completion queue không thể hoàn trả completion");
        }
        return true;
    }

    public void release(int count) {
        if (count < 0) {
            throw new IllegalStateException("Outbox in-flight window release không hợp lệ");
        }
        if (occupied.addAndGet(-count) < 0) {
            occupied.addAndGet(count);
            throw new IllegalStateException("Outbox in-flight window release không hợp lệ");
        }
    }
}
