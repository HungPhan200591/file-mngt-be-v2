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
    @EntityGraph(attributePaths = {"assets", "actressNames", "tagNames"})
    Optional<QuerySubjectEntity> findById(UUID id);

    @Query(
            "select s from QuerySubjectEntity s where (:region is null or s.region = :region) and (:type is null or s.subjectType = :type) and (:rootKey is null or exists (select a.id from QueryAssetEntity a where a.subject = s and a.storageKey = :rootKey)) and (:studio is null or s.studioCode = :studio) and (:actress is null or :actress member of s.actressNames) and (:tag is null or :tag member of s.tagNames) and (lower(s.identityKey) like lower(concat('%', :search, '%')) or lower(coalesce(s.displayTitle, '')) like lower(concat('%', :search, '%')) or lower(coalesce(s.studioCode, '')) like lower(concat('%', :search, '%')))")
    Page<QuerySubjectEntity> search(
            @Param("region") Region region,
            @Param("type") SubjectType type,
            @Param("rootKey") String rootKey,
            @Param("studio") String studio,
            @Param("actress") String actress,
            @Param("tag") String tag,
            @Param("search") String search,
            Pageable pageable);

    @Query(
            "select s from QuerySubjectEntity s where (:region is null or s.region = :region) and (:type is null or s.subjectType = :type) and (:rootKey is null or exists (select a.id from QueryAssetEntity a where a.subject = s and a.storageKey = :rootKey)) and (:studio is null or s.studioCode = :studio) and (:actress is null or :actress member of s.actressNames) and (:tag is null or :tag member of s.tagNames)")
    Page<QuerySubjectEntity> filter(
            @Param("region") Region region,
            @Param("type") SubjectType type,
            @Param("rootKey") String rootKey,
            @Param("studio") String studio,
            @Param("actress") String actress,
            @Param("tag") String tag,
            Pageable pageable);

    @Query(
            "select distinct s.studioCode from QuerySubjectEntity s where s.studioCode is not null order by s.studioCode")
    List<String> listStudios();

    @Query("select distinct actress from QuerySubjectEntity s join s.actressNames actress order by actress")
    List<String> listActresses();

    @Query("select distinct tag from QuerySubjectEntity s join s.tagNames tag order by tag")
    List<String> listTags();

    @Query("select distinct a.storageKey from QueryAssetEntity a where a.storageKey is not null order by a.storageKey")
    List<String> listRootKeys();

    @EntityGraph(attributePaths = {"assets", "actressNames", "tagNames"})
    @Query("select distinct s from QuerySubjectEntity s where s.id in :ids")
    List<QuerySubjectEntity> findAllWithAssetsByIdIn(@Param("ids") Collection<UUID> ids);
}
