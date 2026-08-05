package com.filemngt.v2.scan.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "scan_issue")
/** Issue persistence lưu nguyên nhân file không thể trở thành proposal để người dùng tra cứu hoặc sửa nguồn dữ liệu. */
public class ScanIssueEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

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
