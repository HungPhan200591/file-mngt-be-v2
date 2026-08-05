package com.filemngt.v2.scan.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository ownership của proposal trong database Scan Service. */
public interface ScanProposalRepository extends JpaRepository<ScanProposalEntity, UUID> {
    /** Lấy proposal phân trang cho màn hình review. */
    Page<ScanProposalEntity> findByScanRunId(UUID scanRunId, Pageable pageable);

    /** Lấy toàn bộ proposal của run cho batch decision. */
    List<ScanProposalEntity> findByScanRunId(UUID scanRunId);
}
