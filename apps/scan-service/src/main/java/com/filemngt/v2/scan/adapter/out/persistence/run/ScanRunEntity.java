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
    private String workerId;
    private Instant leaseUntil;
    private int checkpointChunk;
    private Instant checkpointAt;

    protected ScanRunEntity() {}

    public ScanRunEntity(
            UUID id,
            String rootKey,
            ScanProfile profile,
            Instant startedAt,
            Long registryVersion,
            String workerId,
            Instant leaseUntil) {
        this.id = id;
        this.rootKey = rootKey;
        this.profile = profile;
        this.startedAt = startedAt;
        this.status = ScanRunStatus.RUNNING;
        this.registryVersion = registryVersion;
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
    }

    public ScanRunEntity(UUID id, String rootKey, ScanProfile profile, Instant startedAt, Long registryVersion) {
        this(id, rootKey, profile, startedAt, registryVersion, null, null);
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

    /** Kiểm tra lease của run này còn hiệu lực hay không. */
    public boolean isLeaseActive(Instant now) {
        return status == ScanRunStatus.RUNNING && leaseUntil != null && leaseUntil.isAfter(now);
    }

    /** Cập nhật checkpoint và gia hạn lease cho chunk hiện tại. */
    public void updateCheckpoint(
            int chunkIndex, long scannedFiles, long proposals, long issues, Instant nextLeaseUntil) {
        this.checkpointChunk = chunkIndex;
        this.scannedFileCount = scannedFiles;
        this.proposalCount = proposals;
        this.issueCount = issues;
        this.checkpointAt = Instant.now();
        this.leaseUntil = nextLeaseUntil;
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

    public String workerId() {
        return workerId;
    }

    public Instant leaseUntil() {
        return leaseUntil;
    }

    public int checkpointChunk() {
        return checkpointChunk;
    }

    public Instant checkpointAt() {
        return checkpointAt;
    }
}
