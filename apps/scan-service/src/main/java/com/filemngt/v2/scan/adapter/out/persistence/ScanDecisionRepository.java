package com.filemngt.v2.scan.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository ownership của quyết định proposal; khóa chính proposal ID cung cấp idempotency. */
public interface ScanDecisionRepository extends JpaRepository<ScanDecisionEntity, UUID> {}
