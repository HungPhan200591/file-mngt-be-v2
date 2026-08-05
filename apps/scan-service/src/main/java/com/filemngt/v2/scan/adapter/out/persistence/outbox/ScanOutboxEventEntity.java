package com.filemngt.v2.scan.adapter.out.persistence.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "scan_outbox_event")
/** Bản ghi transactional outbox bảo đảm quyết định APPROVE và event phát hiện media được lưu cùng transaction. */
public class ScanOutboxEventEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    private UUID proposalId;
    private String eventType;
    private String partitionKey;
    private String payload;
    private String correlationId;
    private String traceparent;
    private Instant createdAt;
    private Instant publishedAt;
    private int attemptCount;
    private String lastError;

    protected ScanOutboxEventEntity() {}

    public ScanOutboxEventEntity(
            UUID id,
            UUID proposalId,
            String eventType,
            String partitionKey,
            String payload,
            String correlationId,
            String traceparent,
            Instant createdAt) {
        this.id = id;
        this.proposalId = proposalId;
        this.eventType = eventType;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.traceparent = traceparent;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public String partitionKey() {
        return partitionKey;
    }

    public String payload() {
        return payload;
    }

    public String correlationId() {
        return correlationId;
    }

    public String traceparent() {
        return traceparent;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public String lastError() {
        return lastError;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        isNew = false;
    }

    /** Đánh dấu broker đã nhận event và xóa lỗi publish trước đó. */
    public void published() {
        publishedAt = Instant.now();
        lastError = null;
    }

    /** Ghi nhận lần publish thất bại để scheduler có thể thử lại ở chu kỳ sau. */
    public void failed(Exception error) {
        attemptCount++;
        lastError = error.getMessage();
    }
}
