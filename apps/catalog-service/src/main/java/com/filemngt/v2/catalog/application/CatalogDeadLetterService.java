package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterRepository;
import com.filemngt.v2.catalog.application.operation.CatalogOperationDltGateStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogDeadLetterService {

    private final CatalogDeadLetterRepository events;
    private final CatalogOperationDltGateStore dltGates;

    public CatalogDeadLetterService(CatalogDeadLetterRepository events, CatalogOperationDltGateStore dltGates) {
        this.events = events;
        this.dltGates = dltGates;
    }

    @Transactional
    public boolean record(DeadLetterCommand command) {
        if (events.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                command.originalTopic(), command.originalPartition(), command.originalOffset())) {
            return false;
        }
        if (command.operationId() != null) {
            dltGates.lockIfKnown(command.operationId());
        }
        events.saveAndFlush(new CatalogDeadLetterEntity(
                UUID.randomUUID(),
                command.originalTopic(),
                command.originalPartition(),
                command.originalOffset(),
                command.eventKey(),
                command.payload(),
                command.errorDetail(),
                command.operationId(),
                command.routingBucket(),
                command.failureCode(),
                Instant.now()));
        if (command.operationId() != null) {
            dltGates.synchronize(command.operationId());
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
            Integer routingBucket,
            String failureCode) {
        public DeadLetterCommand(
                String originalTopic,
                int originalPartition,
                long originalOffset,
                String eventKey,
                String payload,
                String errorDetail,
                UUID operationId,
                String failureCode) {
            this(
                    originalTopic,
                    originalPartition,
                    originalOffset,
                    eventKey,
                    payload,
                    errorDetail,
                    operationId,
                    null,
                    failureCode);
        }

        public DeadLetterCommand(
                String originalTopic,
                int originalPartition,
                long originalOffset,
                String eventKey,
                String payload,
                String errorDetail) {
            this(originalTopic, originalPartition, originalOffset, eventKey, payload, errorDetail, null, null, null);
        }
    }
}
