package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudioCodeRepository extends JpaRepository<StudioCodeEntity, UUID> {

    boolean existsByRegionAndNormalizedCode(String region, String normalizedCode);

    Optional<StudioCodeEntity> findByRegionAndNormalizedCode(String region, String normalizedCode);

    List<StudioCodeEntity> findByStudioId(UUID studioId);

    List<StudioCodeEntity> findByStudioIdAndActive(UUID studioId, boolean active);

    List<StudioCodeEntity> findByRegionAndActive(String region, boolean active);
}
