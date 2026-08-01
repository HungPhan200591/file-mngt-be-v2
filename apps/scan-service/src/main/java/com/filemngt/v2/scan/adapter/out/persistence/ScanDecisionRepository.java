package com.filemngt.v2.scan.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanDecisionRepository extends JpaRepository<ScanDecisionEntity, UUID> {}
