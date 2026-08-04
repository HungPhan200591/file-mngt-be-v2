package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<TagEntity, UUID> {

    boolean existsByNormalizedName(String normalizedName);

    Page<TagEntity> findByActive(boolean active, Pageable pageable);

    Page<TagEntity> findByDisplayNameContainingIgnoreCase(String name, Pageable pageable);

    Page<TagEntity> findByActiveAndDisplayNameContainingIgnoreCase(boolean active, String name, Pageable pageable);

    List<TagEntity> findByActive(boolean active);
}
