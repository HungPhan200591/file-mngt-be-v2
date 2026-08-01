package com.filemngt.v2.query.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryProcessedEventRepository extends JpaRepository<QueryProcessedEventEntity, UUID> {}
