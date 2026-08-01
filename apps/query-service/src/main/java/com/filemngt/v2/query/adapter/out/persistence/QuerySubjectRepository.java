package com.filemngt.v2.query.adapter.out.persistence;

import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuerySubjectRepository extends JpaRepository<QuerySubjectEntity, UUID> {
    @Override
    @EntityGraph(attributePaths = "assets")
    Optional<QuerySubjectEntity> findById(UUID id);

    @Query(
            "select s from QuerySubjectEntity s where (:region is null or s.region = :region) and (:type is null or s.subjectType = :type) and (lower(s.identityKey) like lower(concat('%', :search, '%')) or lower(coalesce(s.displayTitle, '')) like lower(concat('%', :search, '%')))")
    Page<QuerySubjectEntity> search(
            @Param("region") Region region,
            @Param("type") SubjectType type,
            @Param("search") String search,
            Pageable pageable);

    @Query(
            "select s from QuerySubjectEntity s where (:region is null or s.region = :region) and (:type is null or s.subjectType = :type)")
    Page<QuerySubjectEntity> filter(@Param("region") Region region, @Param("type") SubjectType type, Pageable pageable);

    @EntityGraph(attributePaths = "assets")
    @Query("select distinct s from QuerySubjectEntity s where s.id in :ids")
    List<QuerySubjectEntity> findAllWithAssetsByIdIn(@Param("ids") Collection<UUID> ids);
}
