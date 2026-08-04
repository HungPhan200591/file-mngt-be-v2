package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterDataImportRepository extends JpaRepository<MasterDataImportEntity, UUID> {}
