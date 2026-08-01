package com.filemngt.v2.scan.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "scan_issue")
public class ScanIssueEntity {
    @Id
    private UUID id;

    private UUID scanRunId;
    private String sourceRelativePath;
    private String code;
    private String detail;

    protected ScanIssueEntity() {}

    public ScanIssueEntity(UUID id, UUID runId, String path, String code, String detail) {
        this.id = id;
        scanRunId = runId;
        sourceRelativePath = path;
        this.code = code;
        this.detail = detail;
    }

    public UUID id() {
        return id;
    }

    public String sourceRelativePath() {
        return sourceRelativePath;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }
}
