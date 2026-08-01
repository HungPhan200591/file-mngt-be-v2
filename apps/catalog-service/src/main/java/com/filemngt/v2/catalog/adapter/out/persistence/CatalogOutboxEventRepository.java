package com.filemngt.v2.catalog.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogOutboxEventRepository extends JpaRepository<CatalogOutboxEventEntity, UUID> {
    List<CatalogOutboxEventEntity> findTop20ByPublishedAtIsNullOrderByCreatedAtAsc();

    Optional<CatalogOutboxEventEntity> findFirstByPublishedAtIsNullOrderByCreatedAtAsc();

    long countByPublishedAtIsNull();

    List<CatalogOutboxEventEntity> findBySubjectId(UUID subjectId);

    Page<CatalogOutboxEventEntity> findByPublishedAtIsNull(Pageable pageable);

    Page<CatalogOutboxEventEntity> findByPublishedAtIsNotNull(Pageable pageable);

    Page<CatalogOutboxEventEntity> findByPublishedAtIsNullAndAttemptCountGreaterThan(
            int attemptCount, Pageable pageable);
}
