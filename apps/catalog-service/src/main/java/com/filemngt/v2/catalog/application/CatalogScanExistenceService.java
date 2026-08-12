package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogScanExistenceReadStore;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogScanExistenceReadStore.LocatorKey;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogScanExistenceReadStore.SubjectKey;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogScanExistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogScanExistenceService.class);

    private final CatalogScanExistenceReadStore store;
    private final CatalogScanExistenceMetrics metrics;

    public CatalogScanExistenceService(CatalogScanExistenceReadStore store, CatalogScanExistenceMetrics metrics) {
        this.store = store;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response classify(Request request) {
        Timer.Sample sample = metrics.start(request.items().size());
        try {
            Map<LocatorKey, CatalogScanExistenceReadStore.LocatorMatch> locators =
                    store.findLocators(request.items().stream()
                            .map(item -> new LocatorKey(item.storageKey(), item.relativePath()))
                            .collect(java.util.stream.Collectors.toSet()));
            Map<SubjectKey, CatalogScanExistenceReadStore.SubjectMatch> subjects =
                    store.findSubjects(request.items().stream()
                            .map(item -> new SubjectKey(item.region(), item.subjectType(), item.identityKey()))
                            .collect(java.util.stream.Collectors.toSet()));
            var results = request.items().stream()
                    .map(item -> classify(item, locators, subjects))
                    .toList();
            metrics.complete(sample, results);
            logAggregate(results);
            return new Response(request.scanRunId(), results);
        } catch (RuntimeException exception) {
            metrics.failed(sample);
            throw exception;
        }
    }

    private void logAggregate(List<Result> results) {
        var totals = new EnumMap<Classification, Integer>(Classification.class);
        for (var classification : Classification.values()) totals.put(classification, 0);
        for (var result : results) totals.compute(result.classification(), (ignored, total) -> total + 1);
        LOGGER.info(
                "Catalog scan existence classified items={} exact={} existingSubject={} newSubject={} conflict={}",
                results.size(),
                totals.get(Classification.EXACT_ASSET_EXISTS),
                totals.get(Classification.EXISTING_SUBJECT_NEW_ASSET),
                totals.get(Classification.NEW_SUBJECT),
                totals.get(Classification.CONFLICT));
    }

    private Result classify(
            Candidate candidate,
            Map<LocatorKey, CatalogScanExistenceReadStore.LocatorMatch> locators,
            Map<SubjectKey, CatalogScanExistenceReadStore.SubjectMatch> subjects) {
        var locator = locators.get(new LocatorKey(candidate.storageKey(), candidate.relativePath()));
        if (locator != null) {
            if (locator.region() != candidate.region()
                    || locator.subjectType() != candidate.subjectType()
                    || !locator.identityKey().equals(candidate.identityKey())) {
                return Result.conflict(
                        candidate.clientRef(),
                        locator.subjectId(),
                        locator.assetId(),
                        ConflictCode.LOCATOR_SUBJECT_MISMATCH);
            }
            if (!sameAssetKind(locator.role(), candidate.assetRole())) {
                return Result.conflict(
                        candidate.clientRef(),
                        locator.subjectId(),
                        locator.assetId(),
                        ConflictCode.LOCATOR_ROLE_MISMATCH);
            }
            return new Result(
                    candidate.clientRef(),
                    Classification.EXACT_ASSET_EXISTS,
                    locator.subjectId(),
                    locator.assetId(),
                    null);
        }
        var subject =
                subjects.get(new SubjectKey(candidate.region(), candidate.subjectType(), candidate.identityKey()));
        if (subject == null) {
            return new Result(candidate.clientRef(), Classification.NEW_SUBJECT, null, null, null);
        }
        return new Result(
                candidate.clientRef(), Classification.EXISTING_SUBJECT_NEW_ASSET, subject.subjectId(), null, null);
    }

    private boolean sameAssetKind(MediaAssetRole existing, MediaAssetRole candidate) {
        if (existing == candidate) return true;
        return isVideo(existing) && isVideo(candidate);
    }

    private boolean isVideo(MediaAssetRole role) {
        return role == MediaAssetRole.PRIMARY_VIDEO || role == MediaAssetRole.VIDEO;
    }

    public record Request(UUID scanRunId, List<Candidate> items) {}

    public record Candidate(
            UUID clientRef,
            String storageKey,
            String relativePath,
            Region region,
            SubjectType subjectType,
            String identityKey,
            MediaAssetRole assetRole) {}

    public record Response(UUID scanRunId, List<Result> items) {}

    public record Result(
            UUID clientRef,
            Classification classification,
            UUID matchedSubjectId,
            UUID matchedAssetId,
            ConflictCode conflictCode) {
        private static Result conflict(UUID clientRef, UUID subjectId, UUID assetId, ConflictCode conflictCode) {
            return new Result(clientRef, Classification.CONFLICT, subjectId, assetId, conflictCode);
        }
    }

    public enum Classification {
        EXACT_ASSET_EXISTS,
        EXISTING_SUBJECT_NEW_ASSET,
        NEW_SUBJECT,
        CONFLICT
    }

    public enum ConflictCode {
        LOCATOR_SUBJECT_MISMATCH,
        LOCATOR_ROLE_MISMATCH,
        SUBJECT_PRIMARY_ASSET_EXISTS
    }
}
