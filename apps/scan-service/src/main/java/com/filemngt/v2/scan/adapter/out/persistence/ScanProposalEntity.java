package com.filemngt.v2.scan.adapter.out.persistence;

import com.filemngt.v2.scan.domain.ScanProfile;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "scan_proposal")
public class ScanProposalEntity {
    @Id
    private UUID id;

    private UUID scanRunId;
    private String sourceRelativePath;

    @Enumerated(EnumType.STRING)
    private ScanProfile profile;

    private String candidateType;
    private String identityKey;
    private String displayTitle;
    private String assetRole;
    private String evidence = "{}";

    protected ScanProposalEntity() {}

    public ScanProposalEntity(
            UUID id,
            UUID runId,
            String path,
            ScanProfile profile,
            String type,
            String key,
            String title,
            String role,
            String evidence) {
        this.id = id;
        scanRunId = runId;
        sourceRelativePath = path;
        this.profile = profile;
        candidateType = type;
        identityKey = key;
        displayTitle = title;
        assetRole = role;
        this.evidence = evidence;
    }

    public UUID id() {
        return id;
    }

    public UUID scanRunId() {
        return scanRunId;
    }

    public String sourceRelativePath() {
        return sourceRelativePath;
    }

    public ScanProfile profile() {
        return profile;
    }

    public String candidateType() {
        return candidateType;
    }

    public String identityKey() {
        return identityKey;
    }

    public String displayTitle() {
        return displayTitle;
    }

    public String assetRole() {
        return assetRole;
    }

    public String evidence() {
        return evidence;
    }
}
