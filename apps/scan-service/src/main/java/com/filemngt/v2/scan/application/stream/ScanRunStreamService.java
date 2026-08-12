package com.filemngt.v2.scan.application.stream;

import com.filemngt.v2.scan.application.dto.ScanRunView;
import com.filemngt.v2.scan.application.query.ScanQueryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Dựng snapshot authoritative và phát tín hiệu stream sau khi caller đã commit. */
public class ScanRunStreamService {
    private static final int SCHEMA_VERSION = 1;

    private final ScanQueryService queries;
    private final ScanRunStreamHub hub;

    public ScanRunStreamService(ScanQueryService queries, ScanRunStreamHub hub) {
        this.queries = queries;
        this.hub = hub;
    }

    @Transactional(readOnly = true)
    public ScanRunStreamEvent snapshot(UUID runId) {
        return event(ScanRunStreamEventType.SNAPSHOT, ScanRunStreamPhase.UNKNOWN, queries.getForStream(runId));
    }

    @Transactional(readOnly = true)
    public void publishTerminal(UUID runId) {
        hub.publish(event(ScanRunStreamEventType.TERMINAL, ScanRunStreamPhase.TERMINAL, queries.getForStream(runId)));
    }

    public void publishProgress(ScanRunStreamProgress progress) {
        hub.publish(ScanRunStreamEvent.progress(progress));
    }

    public ScanRunStreamSubscription subscribe(UUID runId, ScanRunStreamSubscriber subscriber) {
        return hub.subscribe(runId, subscriber);
    }

    public void heartbeat() {
        hub.heartbeat();
    }

    private ScanRunStreamEvent event(ScanRunStreamEventType type, ScanRunStreamPhase phase, ScanRunView run) {
        return new ScanRunStreamEvent(
                SCHEMA_VERSION,
                type,
                run.id(),
                Instant.now(),
                phase,
                run.status(),
                run.scannedFileCount(),
                run.scannedFileCount(),
                null,
                null,
                run.proposalCount(),
                run.issueCount(),
                run.finishedAt(),
                run.lastError());
    }
}
