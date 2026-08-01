package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.MediaAssetEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectRepository;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final MediaSubjectRepository repository;
    private final CatalogSubjectOutboxService outbox;

    public CatalogService(MediaSubjectRepository repository, CatalogSubjectOutboxService outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    public SubjectView create(CreateSubjectCommand command) {
        validateAssets(command.assets());
        if (repository.existsByRegionAndSubjectTypeAndIdentityKey(
                command.region(), command.subjectType(), command.identityKey())) {
            throw new DuplicateSubjectException(command.identityKey());
        }
        var now = Instant.now();
        var subject = new MediaSubjectEntity(
                UUID.randomUUID(),
                command.subjectType(),
                command.region(),
                command.identityKey(),
                command.displayTitle(),
                now);
        command.assets()
                .forEach(asset -> subject.addAsset(
                        new MediaAssetEntity(UUID.randomUUID(), asset.role(), asset.relativePath(), now)));
        try {
            var saved = repository.saveAndFlush(subject);
            outbox.enqueue(saved);
            return toView(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateSubjectException(command.identityKey());
        }
    }

    @Transactional(readOnly = true)
    public SubjectView get(UUID subjectId) {
        return repository
                .findById(subjectId)
                .map(this::toView)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
    }

    @Transactional(readOnly = true)
    public PageView list(Region region, SubjectType subjectType, String identityKey, int page, int size) {
        if (identityKey != null && (region == null || subjectType == null)) {
            throw new InvalidListFilterException("identityKey requires both region and subjectType");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MediaSubjectEntity> result = identityKey != null
                ? repository.findByRegionAndSubjectTypeAndIdentityKey(region, subjectType, identityKey, pageable)
                : region != null && subjectType != null
                        ? repository.findByRegionAndSubjectType(region, subjectType, pageable)
                        : region != null
                                ? repository.findByRegion(region, pageable)
                                : subjectType != null
                                        ? repository.findBySubjectType(subjectType, pageable)
                                        : repository.findAll(pageable);
        return new PageView(
                result.map(this::toView).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private SubjectView toView(MediaSubjectEntity subject) {
        var assets = subject.assets().stream()
                .map(asset -> new AssetView(asset.id(), asset.role(), asset.relativePath()))
                .toList();
        return new SubjectView(
                subject.id(),
                subject.subjectType(),
                subject.region(),
                subject.identityKey(),
                subject.displayTitle(),
                subject.createdAt(),
                assets);
    }

    private void validateAssets(List<CreateAssetCommand> assets) {
        if (assets.stream()
                        .filter(asset -> asset.role() == MediaAssetRole.PRIMARY_VIDEO)
                        .count()
                > 1) {
            throw new InvalidAssetException("A subject can have only one PRIMARY_VIDEO asset");
        }
        Set<String> paths = new java.util.HashSet<>();
        if (assets.stream().anyMatch(asset -> !paths.add(asset.relativePath()))) {
            throw new InvalidAssetException("Asset paths must be unique within a subject");
        }
    }

    public record CreateSubjectCommand(
            SubjectType subjectType,
            Region region,
            String identityKey,
            String displayTitle,
            List<CreateAssetCommand> assets) {}

    public record CreateAssetCommand(MediaAssetRole role, String relativePath) {}

    public record SubjectView(
            UUID id,
            SubjectType subjectType,
            Region region,
            String identityKey,
            String displayTitle,
            Instant createdAt,
            List<AssetView> assets) {}

    public record AssetView(UUID id, MediaAssetRole role, String relativePath) {}

    public record PageView(List<SubjectView> content, int page, int size, long totalElements, int totalPages) {}

    public static class DuplicateSubjectException extends RuntimeException {
        public DuplicateSubjectException(String identityKey) {
            super("Subject identity already exists: " + identityKey);
        }
    }

    public static class SubjectNotFoundException extends RuntimeException {
        public SubjectNotFoundException(UUID id) {
            super("Subject does not exist: " + id);
        }
    }

    public static class InvalidAssetException extends RuntimeException {
        public InvalidAssetException(String detail) {
            super(detail);
        }
    }

    public static class InvalidListFilterException extends RuntimeException {
        public InvalidListFilterException(String detail) {
            super(detail);
        }
    }
}
