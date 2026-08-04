package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "master_data_import")
public class MasterDataImportEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String importType;

    @Column(nullable = false)
    private boolean dryRun;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(nullable = false)
    private int totalInput;

    @Column(nullable = false)
    private int createdCount;

    @Column(nullable = false)
    private int mergedCount;

    @Column(nullable = false)
    private int conflictCount;

    private String errorDetail;

    @Column(nullable = false)
    private Instant createdAt;

    protected MasterDataImportEntity() {}

    public MasterDataImportEntity(
            UUID id,
            String importType,
            boolean dryRun,
            String status,
            int totalInput,
            int createdCount,
            int mergedCount,
            int conflictCount,
            String errorDetail,
            Instant createdAt) {
        this.id = id;
        this.importType = importType;
        this.dryRun = dryRun;
        this.status = status;
        this.totalInput = totalInput;
        this.createdCount = createdCount;
        this.mergedCount = mergedCount;
        this.conflictCount = conflictCount;
        this.errorDetail = errorDetail;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String importType() {
        return importType;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public String status() {
        return status;
    }

    public int totalInput() {
        return totalInput;
    }

    public int createdCount() {
        return createdCount;
    }

    public int mergedCount() {
        return mergedCount;
    }

    public int conflictCount() {
        return conflictCount;
    }

    public String errorDetail() {
        return errorDetail;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
