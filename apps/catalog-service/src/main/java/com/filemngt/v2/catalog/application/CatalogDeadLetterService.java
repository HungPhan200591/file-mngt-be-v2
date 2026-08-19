package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogDeadLetterService {

    private final CatalogDeadLetterRepository events;
    private final JdbcTemplate jdbc;

    public CatalogDeadLetterService(CatalogDeadLetterRepository events, JdbcTemplate jdbc) {
        this.events = events;
        this.jdbc = jdbc;
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
                command.operationId(),
                command.failureCode(),
                Instant.now()));
        if (command.operationId() != null) {
            jdbc.update("""
                    update catalog_approval_operation
                    set unresolved_dlt_count = unresolved_dlt_count + 1,
                        status = 'BLOCKED', failure_code = ?, updated_at = now()
                    where operation_id = ? and status <> 'CATALOG_COMMITTED'
                    """, command.failureCode(), command.operationId());
        }
        return true;
    }

    public record DeadLetterCommand(
            String originalTopic,
            int originalPartition,
            long originalOffset,
            String eventKey,
            String payload,
            String errorDetail,
            UUID operationId,
            String failureCode) {
        public DeadLetterCommand(
                String originalTopic,
                int originalPartition,
                long originalOffset,
                String eventKey,
                String payload,
                String errorDetail) {
            this(originalTopic, originalPartition, originalOffset, eventKey, payload, errorDetail, null, null);
        }
    }
}
