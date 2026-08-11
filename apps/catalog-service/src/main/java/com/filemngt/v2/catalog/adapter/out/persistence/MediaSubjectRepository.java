package com.filemngt.v2.catalog.adapter.out.persistence;

import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaSubjectRepository extends JpaRepository<MediaSubjectEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"assets", "actressNames", "tagNames"})
    Optional<MediaSubjectEntity> findById(UUID id);

    boolean existsByRegionAndSubjectTypeAndIdentityKey(Region region, SubjectType subjectType, String identityKey);

    @EntityGraph(attributePaths = {"assets", "actressNames", "tagNames"})
    Optional<MediaSubjectEntity> findByRegionAndSubjectTypeAndIdentityKey(
            Region region, SubjectType subjectType, String identityKey);

    @EntityGraph(attributePaths = "assets")
    Page<MediaSubjectEntity> findByRegionAndSubjectTypeAndIdentityKey(
            Region region, SubjectType subjectType, String identityKey, Pageable pageable);

    Page<MediaSubjectEntity> findByRegionAndSubjectType(Region region, SubjectType subjectType, Pageable pageable);

    Page<MediaSubjectEntity> findByRegion(Region region, Pageable pageable);

    Page<MediaSubjectEntity> findBySubjectType(SubjectType subjectType, Pageable pageable);
}
