package com.filemngt.v2.scan.application.stream;

import com.filemngt.v2.scan.application.exception.ScanRunStreamCapacityExceededException;
import com.filemngt.v2.scan.config.ScanSseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
/** Fan-out process-local, bounded và best-effort cho stream SSE của Scan Service. */
public class ScanRunStreamHub {
    private final Map<UUID, ConcurrentHashMap.KeySetView<ScanRunStreamSubscriber, Boolean>> subscribers =
            new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final ScanSseProperties properties;
    private final Counter opened;
    private final Counter closed;
    private final Counter rejected;

    public ScanRunStreamHub(ScanSseProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        opened = Counter.builder("scan.sse.connections.opened").register(meterRegistry);
        closed = Counter.builder("scan.sse.connections.closed").register(meterRegistry);
        rejected = Counter.builder("scan.sse.connections.rejected").register(meterRegistry);
        Gauge.builder("scan.sse.connections.active", activeConnections, value -> value.doubleValue())
                .register(meterRegistry);
    }

    public ScanRunStreamSubscription subscribe(UUID runId, ScanRunStreamSubscriber subscriber) {
        synchronized (subscribers) {
            var runSubscribers = subscribers.computeIfAbsent(runId, ignored -> ConcurrentHashMap.newKeySet());
            if (activeConnections.get() >= properties.getMaxConnections()
                    || runSubscribers.size() >= properties.getMaxConnectionsPerRun()) {
                rejected.increment();
                throw new ScanRunStreamCapacityExceededException();
            }
            runSubscribers.add(subscriber);
            activeConnections.incrementAndGet();
            opened.increment();
        }
        return () -> remove(runId, subscriber);
    }

    public void publish(ScanRunStreamEvent event) {
        subscribersFor(event.scanId()).forEach(subscriber -> subscriber.accept(event));
    }

    public void heartbeat() {
        subscribers.values().forEach(set -> new ArrayList<>(set).forEach(ScanRunStreamSubscriber::heartbeat));
    }

    @PreDestroy
    public void closeAll() {
        subscribers.values().forEach(set -> new ArrayList<>(set).forEach(ScanRunStreamSubscriber::close));
        subscribers.clear();
        activeConnections.set(0);
    }

    private void remove(UUID runId, ScanRunStreamSubscriber subscriber) {
        synchronized (subscribers) {
            var runSubscribers = subscribers.get(runId);
            if (runSubscribers == null || !runSubscribers.remove(subscriber)) {
                return;
            }
            if (runSubscribers.isEmpty()) {
                subscribers.remove(runId, runSubscribers);
            }
            activeConnections.decrementAndGet();
            closed.increment();
        }
    }

    private ArrayList<ScanRunStreamSubscriber> subscribersFor(UUID runId) {
        var runSubscribers = subscribers.get(runId);
        return runSubscribers == null ? new ArrayList<>() : new ArrayList<>(runSubscribers);
    }
}
