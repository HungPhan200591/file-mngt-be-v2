package com.filemngt.v2.scan.adapter.in.web.sse;

import com.filemngt.v2.scan.application.stream.ScanRunStreamEvent;
import com.filemngt.v2.scan.application.stream.ScanRunStreamEventType;
import com.filemngt.v2.scan.application.stream.ScanRunStreamSubscriber;
import com.filemngt.v2.scan.application.stream.ScanRunStreamSubscription;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Session SSE có queue coalesce để việc gửi tới browser chậm không block scan worker. */
final class ScanRunSseSession implements ScanRunStreamSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanRunSseSession.class);

    private final Object monitor = new Object();
    private final SseEmitter emitter;
    private final Executor sender;
    private final Deque<OutboundMessage> queue = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private ScanRunStreamSubscription subscription = () -> {};
    private ScanRunStreamEvent pendingProgress;
    private boolean initialized;
    private boolean draining;
    private boolean terminalQueued;

    ScanRunSseSession(SseEmitter emitter, Executor sender) {
        this.emitter = emitter;
        this.sender = sender;
        emitter.onCompletion(this::close);
        emitter.onTimeout(this::close);
        emitter.onError(ignored -> close());
    }

    void setSubscription(ScanRunStreamSubscription subscription) {
        boolean closeNow;
        synchronized (monitor) {
            this.subscription = subscription;
            closeNow = closed.get();
        }
        if (closeNow) {
            subscription.close();
        }
    }

    void activate(ScanRunStreamEvent snapshot) {
        synchronized (monitor) {
            if (closed.get()) {
                return;
            }
            initialized = true;
            queue.addLast(OutboundMessage.event(snapshot));
            flushPendingProgress();
        }
        requestDrain();
    }

    @Override
    public void accept(ScanRunStreamEvent event) {
        synchronized (monitor) {
            if (closed.get() || terminalQueued) {
                return;
            }
            if (event.eventType() == ScanRunStreamEventType.PROGRESS) {
                pendingProgress = event;
            } else {
                flushPendingProgress();
                queue.addLast(OutboundMessage.event(event));
                terminalQueued = event.eventType() == ScanRunStreamEventType.TERMINAL;
            }
            if (!initialized) {
                return;
            }
        }
        requestDrain();
    }

    @Override
    public void heartbeat() {
        synchronized (monitor) {
            if (closed.get() || !initialized || terminalQueued) {
                return;
            }
            flushPendingProgress();
            queue.addLast(OutboundMessage.keepalive());
        }
        requestDrain();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        subscription.close();
        emitter.complete();
    }

    private void requestDrain() {
        synchronized (monitor) {
            if (draining || closed.get()) {
                return;
            }
            draining = true;
        }
        try {
            sender.execute(this::drain);
        } catch (RuntimeException exception) {
            LOGGER.debug("Không thể schedule SSE sender do executor đã dừng: failureType={}", exception.getClass().getSimpleName());
            close();
        }
    }

    private void drain() {
        try {
            OutboundMessage message;
            while ((message = nextMessage()) != null) {
                send(message);
                if (message.isTerminal()) {
                    close();
                    return;
                }
            }
        } finally {
            boolean reschedule;
            synchronized (monitor) {
                draining = false;
                reschedule = !closed.get() && hasMessages();
            }
            if (reschedule) {
                requestDrain();
            }
        }
    }

    private OutboundMessage nextMessage() {
        synchronized (monitor) {
            if (closed.get()) {
                return null;
            }
            OutboundMessage next = queue.pollFirst();
            if (next != null) {
                return next;
            }
            if (pendingProgress == null) {
                return null;
            }
            next = OutboundMessage.event(pendingProgress);
            pendingProgress = null;
            return next;
        }
    }

    private void send(OutboundMessage message) {
        try {
            if (message.heartbeat()) {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } else {
                emitter.send(SseEmitter.event().name(message.event().eventName()).data(message.event()));
            }
        } catch (IOException exception) {
            LOGGER.debug("SSE client đã ngắt kết nối: failureType={}", exception.getClass().getSimpleName());
            close();
        }
    }

    private void flushPendingProgress() {
        if (pendingProgress != null) {
            queue.addLast(OutboundMessage.event(pendingProgress));
            pendingProgress = null;
        }
    }

    private boolean hasMessages() {
        return !queue.isEmpty() || pendingProgress != null;
    }

    private record OutboundMessage(ScanRunStreamEvent event, boolean heartbeat) {
        static OutboundMessage event(ScanRunStreamEvent event) {
            return new OutboundMessage(event, false);
        }

        static OutboundMessage keepalive() {
            return new OutboundMessage(null, true);
        }

        boolean isTerminal() {
            return event != null && event.eventType() == ScanRunStreamEventType.TERMINAL;
        }
    }
}
