package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanFileInventoryRepository extends JpaRepository<ScanFileInventoryEntity, UUID> {
    Optional<ScanFileInventoryEntity> findByRootKeyAndSourceRelativePath(String rootKey, String sourceRelativePath);

    @Query("""
            select inventory.sourceRelativePath
            from ScanFileInventoryEntity inventory
            where inventory.rootKey = :rootKey
              and inventory.sourceRelativePath in :paths
              and inventory.state = :state
            """)
    List<String> findPathsByRootKeyAndSourceRelativePathInAndState(
            @Param("rootKey") String rootKey,
            @Param("paths") Collection<String> paths,
            @Param("state") ScanFileInventoryState state);
}
