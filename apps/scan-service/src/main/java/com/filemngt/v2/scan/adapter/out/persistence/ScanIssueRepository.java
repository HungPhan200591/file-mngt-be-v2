package com.filemngt.v2.scan.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanIssueRepository extends JpaRepository<ScanIssueEntity, UUID> {
    Page<ScanIssueEntity> findByScanRunId(UUID scanRunId, Pageable pageable);

    Page<ScanIssueEntity> findByScanRunIdAndCode(UUID scanRunId, String code, Pageable pageable);

    Page<ScanIssueEntity> findByScanRunIdAndSourceRelativePathContainingIgnoreCaseOrDetailContainingIgnoreCase(
            UUID scanRunId, String pathSearch, String detailSearch, Pageable pageable);

    Page<ScanIssueEntity> findByScanRunIdAndCodeAndSourceRelativePathContainingIgnoreCaseOrDetailContainingIgnoreCase(
            UUID scanRunId, String code, String pathSearch, String detailSearch, Pageable pageable);
}
