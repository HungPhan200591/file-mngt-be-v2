package com.filemngt.v2.scan.adapter.out.persistence.approval;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository của durable approval operation trong `scan_db`. */
public interface ApprovalOperationRepository extends JpaRepository<ApprovalOperationEntity, UUID> {
    boolean existsByScanRunIdAndStatusIn(UUID scanRunId, Collection<String> statuses);

    @Query(
            "select operation.scanRunId from ApprovalOperationEntity operation where operation.scanRunId in :scanRunIds and operation.status in ('ACCEPTED', 'RUNNING')")
    List<UUID> findActiveScanRunIds(@Param("scanRunIds") Collection<UUID> scanRunIds);

    @Query(
            value =
                    "select * from scan_approval_operation where status = 'ACCEPTED' or (status = 'RUNNING' and lease_until < :now) order by accepted_at, id limit 1 for update skip locked",
            nativeQuery = true)
    List<ApprovalOperationEntity> lockNext(@Param("now") Instant now);

    @Query(value = "select * from scan_approval_operation where id = :id for update", nativeQuery = true)
    Optional<ApprovalOperationEntity> lockById(@Param("id") UUID id);
}
