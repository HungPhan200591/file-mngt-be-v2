package com.filemngt.v2.catalog.adapter.out.persistence;

import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogScanExistenceReadStore {

    private final EntityManager entityManager;

    public CatalogScanExistenceReadStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Map<LocatorKey, LocatorMatch> findLocators(Set<LocatorKey> requestedLocators) {
        if (requestedLocators.isEmpty()) {
            return Map.of();
        }
        var locators = new LinkedHashSet<>(requestedLocators);
        String sql = """
                WITH requested(storage_key, relative_path) AS (VALUES %s)
                SELECT asset.storage_key, asset.relative_path, asset.id, subject.id,
                       subject.region, subject.subject_type, subject.identity_key, asset.role
                FROM requested
                JOIN media_asset asset
                  ON asset.storage_key = requested.storage_key
                 AND asset.relative_path = requested.relative_path
                JOIN media_subject subject ON subject.id = asset.subject_id
                """.formatted(values(locators.size(), 2));
        var query = entityManager.createNativeQuery(sql);
        int parameter = 1;
        for (LocatorKey locator : locators) {
            query.setParameter(parameter++, locator.storageKey());
            query.setParameter(parameter++, locator.relativePath());
        }
        Map<LocatorKey, LocatorMatch> matches = new LinkedHashMap<>();
        for (Object[] row : rows(query.getResultList())) {
            var key = new LocatorKey((String) row[0], (String) row[1]);
            matches.put(
                    key,
                    new LocatorMatch(
                            (UUID) row[2],
                            (UUID) row[3],
                            Region.valueOf(row[4].toString()),
                            SubjectType.valueOf(row[5].toString()),
                            (String) row[6],
                            MediaAssetRole.valueOf(row[7].toString())));
        }
        return Map.copyOf(matches);
    }

    public Map<SubjectKey, SubjectMatch> findSubjects(Set<SubjectKey> requestedSubjects) {
        if (requestedSubjects.isEmpty()) {
            return Map.of();
        }
        var subjects = new LinkedHashSet<>(requestedSubjects);
        String sql = """
                WITH requested(region, subject_type, identity_key) AS (VALUES %s)
                SELECT subject.id, subject.region, subject.subject_type, subject.identity_key,
                       EXISTS (
                           SELECT 1 FROM media_asset asset
                           WHERE asset.subject_id = subject.id AND asset.role = 'PRIMARY_VIDEO'
                       )
                FROM requested
                JOIN media_subject subject
                  ON subject.region = requested.region
                 AND subject.subject_type = requested.subject_type
                 AND subject.identity_key = requested.identity_key
                """.formatted(values(subjects.size(), 3));
        var query = entityManager.createNativeQuery(sql);
        int parameter = 1;
        for (SubjectKey subject : subjects) {
            query.setParameter(parameter++, subject.region().name());
            query.setParameter(parameter++, subject.subjectType().name());
            query.setParameter(parameter++, subject.identityKey());
        }
        Map<SubjectKey, SubjectMatch> matches = new LinkedHashMap<>();
        for (Object[] row : rows(query.getResultList())) {
            var key = new SubjectKey(
                    Region.valueOf(row[1].toString()), SubjectType.valueOf(row[2].toString()), (String) row[3]);
            matches.put(key, new SubjectMatch((UUID) row[0], (Boolean) row[4]));
        }
        return Map.copyOf(matches);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(List<?> rows) {
        return (List<Object[]>) rows;
    }

    private String values(int rowCount, int columns) {
        String row = "("
                + IntStream.range(0, columns)
                        .mapToObj(ignored -> "?")
                        .collect(java.util.stream.Collectors.joining(", "))
                + ")";
        return IntStream.range(0, rowCount).mapToObj(ignored -> row).collect(java.util.stream.Collectors.joining(", "));
    }

    public record LocatorKey(String storageKey, String relativePath) {}

    public record LocatorMatch(
            UUID assetId,
            UUID subjectId,
            Region region,
            SubjectType subjectType,
            String identityKey,
            MediaAssetRole role) {}

    public record SubjectKey(Region region, SubjectType subjectType, String identityKey) {}

    public record SubjectMatch(UUID subjectId, boolean hasPrimaryVideo) {}
}
