package com.filemngt.v2.catalog.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CatalogOutboxEventRepository extends JpaRepository<CatalogOutboxEventEntity, UUID> {
    List<CatalogOutboxEventEntity> findTop20ByPublishedAtIsNullOrderByCreatedAtAsc();

    @Query(value = "select * from catalog_outbox_event where published_at is null and (lease_until is null or lease_until < :now) order by created_at, id limit :limit for update skip locked", nativeQuery = true)
    List<CatalogOutboxEventEntity> lockClaimable(@Param("now") Instant now, @Param("limit") int limit);

    Optional<CatalogOutboxEventEntity> findFirstByPublishedAtIsNullOrderByCreatedAtAsc();

    long countByPublishedAtIsNull();

    @Modifying
    @Transactional
    @Query("update CatalogOutboxEventEntity e set e.publishedAt = :now, e.lastError = null, e.leaseOwner = null, e.leaseUntil = null where e.id = :id and e.leaseOwner = :owner and e.publishedAt is null")
    int markPublished(@Param("id") UUID id, @Param("owner") String owner, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("update CatalogOutboxEventEntity e set e.attemptCount = e.attemptCount + 1, e.lastError = :error, e.leaseOwner = null, e.leaseUntil = null where e.id = :id and e.leaseOwner = :owner and e.publishedAt is null")
    int markFailed(@Param("id") UUID id, @Param("owner") String owner, @Param("error") String error);

    List<CatalogOutboxEventEntity> findBySubjectId(UUID subjectId);

    Page<CatalogOutboxEventEntity> findByPublishedAtIsNull(Pageable pageable);

    Page<CatalogOutboxEventEntity> findByPublishedAtIsNotNull(Pageable pageable);

    Page<CatalogOutboxEventEntity> findByPublishedAtIsNullAndAttemptCountGreaterThan(
            int attemptCount, Pageable pageable);
}
