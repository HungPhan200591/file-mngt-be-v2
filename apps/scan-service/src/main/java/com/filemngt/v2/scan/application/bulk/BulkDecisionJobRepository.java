package com.filemngt.v2.scan.application.bulk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BulkDecisionJobRepository extends JpaRepository<BulkDecisionJobEntity, UUID> {
    @Query(
            value =
                    "select * from scan_bulk_decision_job where status = 'PENDING' or (status = 'RUNNING' and lease_until < :now) order by created_at, id limit 1 for update skip locked",
            nativeQuery = true)
    List<BulkDecisionJobEntity> lockNext(@Param("now") Instant now);
}
