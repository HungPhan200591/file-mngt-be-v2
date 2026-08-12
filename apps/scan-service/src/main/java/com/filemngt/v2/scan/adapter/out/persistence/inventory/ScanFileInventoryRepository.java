package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanFileInventoryRepository extends JpaRepository<ScanFileInventoryEntity, UUID> {
    Optional<ScanFileInventoryEntity> findByRootKeyAndSourceRelativePath(String rootKey, String sourceRelativePath);
}
