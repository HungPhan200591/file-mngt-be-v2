package com.filemngt.v2.scan.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanOutboxEventRepository extends JpaRepository<ScanOutboxEventEntity, UUID> {
    List<ScanOutboxEventEntity> findTop20ByPublishedAtIsNullOrderByCreatedAtAsc();
}
