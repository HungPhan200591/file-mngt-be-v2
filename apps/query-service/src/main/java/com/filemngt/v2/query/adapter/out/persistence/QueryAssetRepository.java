package com.filemngt.v2.query.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueryAssetRepository extends JpaRepository<QueryAssetEntity, UUID> {

    @Query(
            "select a.id from QueryAssetEntity a where a.role in (com.filemngt.v2.query.domain.MediaAssetRole.PRIMARY_VIDEO, com.filemngt.v2.query.domain.MediaAssetRole.VIDEO) and (:rootKey is null or a.storageKey = :rootKey) and (:studio is null or a.subject.studioCode = :studio) and (:actress is null or :actress member of a.subject.actressNames) and (:tag is null or :tag member of a.tagNames) and (:search is null or lower(a.subject.identityKey) like lower(concat('%', :search, '%')) or lower(coalesce(a.subject.displayTitle, '')) like lower(concat('%', :search, '%')) or lower(a.relativePath) like lower(concat('%', :search, '%'))) ")
    Page<UUID> findVideoIds(
            @Param("rootKey") String rootKey,
            @Param("studio") String studio,
            @Param("actress") String actress,
            @Param("tag") String tag,
            @Param("search") String search,
            Pageable pageable);

    @EntityGraph(attributePaths = {"tagNames", "subject"})
    @Query("select distinct a from QueryAssetEntity a where a.id in :ids")
    List<QueryAssetEntity> findAllWithTagsByIdIn(@Param("ids") Collection<UUID> ids);

    @Query(
            "select distinct tag from QueryAssetEntity a join a.tagNames tag where a.role in (com.filemngt.v2.query.domain.MediaAssetRole.PRIMARY_VIDEO, com.filemngt.v2.query.domain.MediaAssetRole.VIDEO) order by tag")
    List<String> listVideoTags();
}
