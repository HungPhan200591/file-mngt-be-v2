package com.filemngt.v2.scan.adapter.out.persistence;

import com.filemngt.v2.scan.domain.ScanRunStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRunRepository extends JpaRepository<ScanRunEntity, UUID> {
    boolean existsByRootKeyAndStatus(String rootKey, ScanRunStatus status);
    List<ScanRunEntity> findByRootKeyAndStatus(String rootKey, ScanRunStatus status);
}
