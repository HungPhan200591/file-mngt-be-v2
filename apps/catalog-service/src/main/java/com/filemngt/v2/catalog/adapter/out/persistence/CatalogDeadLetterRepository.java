package com.filemngt.v2.catalog.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogDeadLetterRepository extends JpaRepository<CatalogDeadLetterEntity, UUID> {
    boolean existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
            String originalTopic, int originalPartition, long originalOffset);

    Page<CatalogDeadLetterEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
