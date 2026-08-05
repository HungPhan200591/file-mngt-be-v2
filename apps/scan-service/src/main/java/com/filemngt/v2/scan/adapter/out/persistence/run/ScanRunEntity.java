package com.filemngt.v2.scan.adapter.out.persistence.run;

import com.filemngt.v2.scan.domain.scan.ScanProfile;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_run")
/** Aggregate persistence lưu vòng đời và số liệu tổng kết của một lần scan root. */
public class ScanRunEntity {
    @Id
    private UUID id;

    private String rootKey;

    @Enumerated(EnumType.STRING)
    private ScanProfile profile;

    @Enumerated(EnumType.STRING)
    private ScanRunStatus status;

    private Instant startedAt;
    private Instant finishedAt;
    private long scannedFileCount;
    private long proposalCount;
    private long issueCount;
    private String lastError;
    private Long registryVersion;

    protected ScanRunEntity() {}

    public ScanRunEntity(UUID id, String rootKey, ScanProfile profile, Instant startedAt, Long registryVersion) {
        this.id = id;
        this.rootKey = rootKey;
        this.profile = profile;
        this.startedAt = startedAt;
        this.status = ScanRunStatus.RUNNING;
        this.registryVersion = registryVersion;
    }

    /** Đóng scan thành công bằng số liệu executor đã tích lũy. */
    public void complete(long files, long proposals, long issues) {
        scannedFileCount = files;
        proposalCount = proposals;
        issueCount = issues;
        finishedAt = Instant.now();
        status = ScanRunStatus.COMPLETED;
    }

    /** Đóng scan thất bại và giữ nguyên nhân cuối để API hiển thị. */
    public void fail(String error) {
        finishedAt = Instant.now();
        lastError = error;
        status = ScanRunStatus.FAILED;
    }

    public UUID id() {
        return id;
    }

    public String rootKey() {
        return rootKey;
    }

    public ScanProfile profile() {
        return profile;
    }

    public ScanRunStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public long scannedFileCount() {
        return scannedFileCount;
    }

    public long proposalCount() {
        return proposalCount;
    }

    public long issueCount() {
        return issueCount;
    }

    public String lastError() {
        return lastError;
    }

    public Long registryVersion() {
        return registryVersion;
    }
}
