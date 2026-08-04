package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MasterDataRegistryRepository extends JpaRepository<MasterDataRegistryEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MasterDataRegistryEntity r where r.id = 1")
    Optional<MasterDataRegistryEntity> findForUpdate();
}
