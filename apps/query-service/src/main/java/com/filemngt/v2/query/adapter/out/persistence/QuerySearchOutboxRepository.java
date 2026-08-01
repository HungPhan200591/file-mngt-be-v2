package com.filemngt.v2.query.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuerySearchOutboxRepository extends JpaRepository<QuerySearchOutboxEntity, UUID> {
    List<QuerySearchOutboxEntity> findTop100ByIndexedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Instant now);

    long countByIndexedAtIsNull();
}
