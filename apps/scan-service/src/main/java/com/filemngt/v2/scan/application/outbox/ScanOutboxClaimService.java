package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScanOutboxClaimService {
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final ScanOutboxEventRepository events;

    public ScanOutboxClaimService(ScanOutboxEventRepository events) {
        this.events = events;
    }

    @Transactional
    public List<ScanOutboxEventEntity> claim(String owner, int limit) {
        Instant now = Instant.now();
        var claimed = events.lockClaimable(now, limit);
        Instant until = now.plus(LEASE);
        claimed.forEach(event -> event.claim(owner, until));
        events.saveAll(claimed);
        return List.copyOf(claimed);
    }
}
