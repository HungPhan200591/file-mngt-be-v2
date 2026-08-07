package com.filemngt.v2.scan.adapter.in.web.sse;

import com.filemngt.v2.scan.application.stream.ScanRunStreamService;
import com.filemngt.v2.scan.config.ScanSseProperties;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
/** Adapter HTTP mở stream sau khi đã đăng ký subscriber, rồi gửi snapshot đầu tiên. */
public class ScanRunSseStreamAdapter {
    private final ScanRunStreamService streamService;
    private final ScanSseProperties properties;
    private final Executor sender;

    public ScanRunSseStreamAdapter(
            ScanRunStreamService streamService,
            ScanSseProperties properties,
            @Qualifier("scanSseSenderExecutor") Executor sender) {
        this.streamService = streamService;
        this.properties = properties;
        this.sender = sender;
    }

    public SseEmitter open(UUID runId) {
        var emitter = new SseEmitter(properties.getConnectionLifetimeSeconds() * 1000);
        var session = new ScanRunSseSession(emitter, sender);
        try {
            session.setSubscription(streamService.subscribe(runId, session));
            session.activate(streamService.snapshot(runId));
            return emitter;
        } catch (RuntimeException exception) {
            session.close();
            throw exception;
        }
    }
}
