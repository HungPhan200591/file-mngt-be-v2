package com.filemngt.v2.catalog.adapter.out.persistence;

import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaSubjectRepository extends JpaRepository<MediaSubjectEntity, UUID> {

    boolean existsByRegionAndSubjectTypeAndIdentityKey(Region region, SubjectType subjectType, String identityKey);

    Page<MediaSubjectEntity> findByRegionAndSubjectType(Region region, SubjectType subjectType, Pageable pageable);

    Page<MediaSubjectEntity> findByRegion(Region region, Pageable pageable);

    Page<MediaSubjectEntity> findBySubjectType(SubjectType subjectType, Pageable pageable);
}
