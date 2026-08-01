package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogDeadLetterService {

    private final CatalogDeadLetterRepository events;

    public CatalogDeadLetterService(CatalogDeadLetterRepository events) {
        this.events = events;
    }

    @Transactional
    public boolean record(DeadLetterCommand command) {
        if (events.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                command.originalTopic(), command.originalPartition(), command.originalOffset())) {
            return false;
        }
        events.save(new CatalogDeadLetterEntity(
                UUID.randomUUID(),
                command.originalTopic(),
                command.originalPartition(),
                command.originalOffset(),
                command.eventKey(),
                command.payload(),
                command.errorDetail(),
                Instant.now()));
        return true;
    }

    public record DeadLetterCommand(
            String originalTopic,
            int originalPartition,
            long originalOffset,
            String eventKey,
            String payload,
            String errorDetail) {}
}
