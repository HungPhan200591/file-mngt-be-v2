package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudioRepository extends JpaRepository<StudioEntity, UUID> {

    boolean existsByRegionAndNormalizedName(String region, String normalizedName);

    Optional<StudioEntity> findByRegionAndNormalizedName(String region, String normalizedName);

    Page<StudioEntity> findByRegion(String region, Pageable pageable);

    Page<StudioEntity> findByRegionAndActive(String region, boolean active, Pageable pageable);

    Page<StudioEntity> findByActive(boolean active, Pageable pageable);

    Page<StudioEntity> findByRegionAndDisplayNameContainingIgnoreCase(String region, String name, Pageable pageable);

    Page<StudioEntity> findByDisplayNameContainingIgnoreCase(String name, Pageable pageable);

    Page<StudioEntity> findByRegionAndActiveAndDisplayNameContainingIgnoreCase(
            String region, boolean active, String name, Pageable pageable);

    Page<StudioEntity> findByActiveAndDisplayNameContainingIgnoreCase(boolean active, String name, Pageable pageable);

    List<StudioEntity> findByRegionAndActive(String region, boolean active);
}
