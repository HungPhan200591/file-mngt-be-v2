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
    String GALLERY_CARD_PREDICATE =
            " from QueryAssetEntity a where (:rootKey is null or a.storageKey = :rootKey) and (a.role in (com.filemngt.v2.query.domain.MediaAssetRole.PRIMARY_VIDEO, com.filemngt.v2.query.domain.MediaAssetRole.VIDEO) or (a.role in (com.filemngt.v2.query.domain.MediaAssetRole.IMAGE, com.filemngt.v2.query.domain.MediaAssetRole.GIF) and not exists (select video.id from QueryAssetEntity video where video.subject = a.subject and (:rootKey is null or video.storageKey = :rootKey) and video.role in (com.filemngt.v2.query.domain.MediaAssetRole.PRIMARY_VIDEO, com.filemngt.v2.query.domain.MediaAssetRole.VIDEO)) and not exists (select preferred.id from QueryAssetEntity preferred where preferred.subject = a.subject and (:rootKey is null or preferred.storageKey = :rootKey) and preferred.role in (com.filemngt.v2.query.domain.MediaAssetRole.IMAGE, com.filemngt.v2.query.domain.MediaAssetRole.GIF) and ((a.role = com.filemngt.v2.query.domain.MediaAssetRole.GIF and preferred.role = com.filemngt.v2.query.domain.MediaAssetRole.IMAGE) or (preferred.role = a.role and preferred.relativePath < a.relativePath)))) and (:studio is null or a.subject.studioCode = :studio) and (:actress is null or :actress member of a.subject.actressNames) and (:tag is null or :tag member of a.tagNames) and (cast(:search as String) is null or lower(a.subject.identityKey) like lower(concat('%', cast(:search as String), '%')) or lower(coalesce(a.subject.displayTitle, '')) like lower(concat('%', cast(:search as String), '%')) or lower(a.relativePath) like lower(concat('%', cast(:search as String), '%')))) ";

    @Query(value = "select a.id" + GALLERY_CARD_PREDICATE, countQuery = "select count(a.id)" + GALLERY_CARD_PREDICATE)
    Page<UUID> findGalleryCardIds(
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
