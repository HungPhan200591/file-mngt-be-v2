package com.filemngt.v2.scan.adapter.out.persistence.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Repository ownership của transactional outbox, chỉ dùng trong Scan Service. */
public interface ScanOutboxEventRepository extends JpaRepository<ScanOutboxEventEntity, UUID> {
    /** Lấy batch nhỏ event cũ nhất chưa publish để scheduler gửi theo thứ tự tạo. */
    List<ScanOutboxEventEntity> findTop20ByPublishedAtIsNullOrderByCreatedAtAsc();

    @Query(
            value =
                    "select * from scan_outbox_event where published_at is null and (lease_until is null or lease_until < :now) order by created_at, id limit :limit for update skip locked",
            nativeQuery = true)
    List<ScanOutboxEventEntity> lockClaimable(@Param("now") Instant now, @Param("limit") int limit);

    Optional<ScanOutboxEventEntity> findFirstByPublishedAtIsNullOrderByCreatedAtAsc();

    long countByPublishedAtIsNull();

    @Modifying
    @Transactional
    @Query(
            "update ScanOutboxEventEntity e set e.publishedAt = :now, e.lastError = null, e.leaseOwner = null, e.leaseUntil = null where e.id = :id and e.leaseOwner = :owner and e.publishedAt is null")
    int markPublished(@Param("id") UUID id, @Param("owner") String owner, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query(
            "update ScanOutboxEventEntity e set e.publishedAt = :now, e.lastError = null, e.leaseOwner = null, e.leaseUntil = null where e.id in :ids and e.leaseOwner = :owner and e.publishedAt is null")
    int markPublishedBatch(@Param("ids") List<UUID> ids, @Param("owner") String owner, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query(
            "update ScanOutboxEventEntity e set e.attemptCount = e.attemptCount + 1, e.lastError = :error, e.leaseOwner = null, e.leaseUntil = null where e.id = :id and e.leaseOwner = :owner and e.publishedAt is null")
    int markFailed(@Param("id") UUID id, @Param("owner") String owner, @Param("error") String error);
}
