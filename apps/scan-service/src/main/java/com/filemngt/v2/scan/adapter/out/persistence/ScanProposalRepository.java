package com.filemngt.v2.scan.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanProposalRepository extends JpaRepository<ScanProposalEntity, UUID> { Page<ScanProposalEntity> findByScanRunId(UUID scanRunId, Pageable pageable); }
