package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogOutboxClaimService {
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final CatalogOutboxEventRepository events;

    public CatalogOutboxClaimService(CatalogOutboxEventRepository events) {
        this.events = events;
    }

    @Transactional
    public List<CatalogOutboxEventEntity> claim(String owner, int limit) {
        Instant now = Instant.now();
        var claimed = events.lockClaimable(now, limit);
        Instant until = now.plus(LEASE);
        claimed.forEach(event -> event.claim(owner, until));
        events.saveAll(claimed);
        return List.copyOf(claimed);
    }
}
