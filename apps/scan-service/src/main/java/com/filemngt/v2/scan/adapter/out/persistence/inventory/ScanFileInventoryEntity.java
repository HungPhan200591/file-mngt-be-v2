package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "scan_file_inventory")
/** Inventory persistence đại diện cho thông tin file vật lý đã được quét trên ổ đĩa. */
public class ScanFileInventoryEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    private String rootKey;
    private String sourceRelativePath;
    private long fileSize;
    private Instant fileModifiedAt;

    @Enumerated(EnumType.STRING)
    private ScanFileInventoryState state;

    private Instant createdAt;
    private Instant updatedAt;

    protected ScanFileInventoryEntity() {}

    public ScanFileInventoryEntity(
            UUID id,
            String rootKey,
            String sourceRelativePath,
            long fileSize,
            Instant fileModifiedAt,
            ScanFileInventoryState state) {
        this.id = id;
        this.rootKey = rootKey;
        this.sourceRelativePath = sourceRelativePath;
        this.fileSize = fileSize;
        this.fileModifiedAt = fileModifiedAt;
        this.state = state;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateMetadata(long fileSize, Instant fileModifiedAt, ScanFileInventoryState state) {
        this.fileSize = fileSize;
        this.fileModifiedAt = fileModifiedAt;
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public String rootKey() {
        return rootKey;
    }

    public String sourceRelativePath() {
        return sourceRelativePath;
    }

    public long fileSize() {
        return fileSize;
    }

    public Instant fileModifiedAt() {
        return fileModifiedAt;
    }

    public ScanFileInventoryState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }
}
