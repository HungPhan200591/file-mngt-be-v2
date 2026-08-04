package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActressRepository extends JpaRepository<ActressEntity, UUID> {

    boolean existsByRegionAndNormalizedName(String region, String normalizedName);

    java.util.Optional<ActressEntity> findByRegionAndNormalizedName(String region, String normalizedName);

    Page<ActressEntity> findByRegion(String region, Pageable pageable);

    Page<ActressEntity> findByRegionAndActive(String region, boolean active, Pageable pageable);

    Page<ActressEntity> findByActive(boolean active, Pageable pageable);

    Page<ActressEntity> findByRegionAndDisplayNameContainingIgnoreCase(String region, String name, Pageable pageable);

    Page<ActressEntity> findByDisplayNameContainingIgnoreCase(String name, Pageable pageable);

    Page<ActressEntity> findByRegionAndActiveAndDisplayNameContainingIgnoreCase(
            String region, boolean active, String name, Pageable pageable);

    Page<ActressEntity> findByActiveAndDisplayNameContainingIgnoreCase(boolean active, String name, Pageable pageable);
}
