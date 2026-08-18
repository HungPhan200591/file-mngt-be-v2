package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScanOutboxClaimService {
    private final ScanOutboxEventRepository events;
    private final OutboxDrainProperties properties;

    public ScanOutboxClaimService(ScanOutboxEventRepository events, OutboxDrainProperties properties) {
        this.events = events;
        this.properties = properties;
    }

    @Transactional
    public List<ScanOutboxEventEntity> claim(String owner, int limit) {
        Instant now = Instant.now();
        var claimed = events.lockClaimable(now, limit);
        Instant until = now.plus(Duration.ofSeconds(properties.getLeaseSeconds()));
        claimed.forEach(event -> event.claim(owner, until));
        events.saveAll(claimed);
        return List.copyOf(claimed);
    }
}
