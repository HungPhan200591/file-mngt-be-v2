package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanInventorySnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanFileInventoryRepository extends JpaRepository<ScanFileInventoryEntity, UUID> {

    /**
     * Lấy snapshot nhẹ (chỉ path + fileSize + fileModifiedAt) để so sánh với file trên đĩa.
     * Dùng trong chunk 500 file: 1 câu IN thay vì N+1 query.
     */
    @Query("""
            SELECT new com.filemngt.v2.scan.domain.inventory.ScanInventorySnapshot(
                e.sourceRelativePath, e.fileSize, e.fileModifiedAt)
            FROM ScanFileInventoryEntity e
            WHERE e.rootKey = :rootKey
              AND e.sourceRelativePath IN :paths
            """)
    List<ScanInventorySnapshot> findSnapshotsByRootKeyAndPaths(
            @Param("rootKey") String rootKey, @Param("paths") List<String> paths);
}
