package com.filemngt.v2.catalog.masterdata.application;

import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.TagEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.TagRepository;
import com.filemngt.v2.catalog.masterdata.application.dto.MasterDataPageView;
import com.filemngt.v2.catalog.masterdata.application.dto.TagView;
import com.filemngt.v2.catalog.masterdata.application.exception.DuplicateMasterDataException;
import com.filemngt.v2.catalog.masterdata.application.exception.TagNotFoundException;
import com.filemngt.v2.catalog.masterdata.domain.MasterDataNormalizer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataTagService {

    private final TagRepository tagRepository;
    private final MasterDataVersionService versionService;

    public MasterDataTagService(TagRepository tagRepository, MasterDataVersionService versionService) {
        this.tagRepository = tagRepository;
        this.versionService = versionService;
    }

    @Transactional(readOnly = true)
    public MasterDataPageView<TagView> list(String name, Boolean active, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("displayName"));
        Page<TagEntity> result;
        if (name != null && active != null) {
            result = tagRepository.findByActiveAndDisplayNameContainingIgnoreCase(active, name, pageable);
        } else if (name != null) {
            result = tagRepository.findByDisplayNameContainingIgnoreCase(name, pageable);
        } else if (active != null) {
            result = tagRepository.findByActive(active, pageable);
        } else {
            result = tagRepository.findAll(pageable);
        }
        return toPage(result.map(this::toView));
    }

    @Transactional
    public TagView create(String displayName) {
        var normalized = MasterDataNormalizer.normalizeName(displayName);
        if (tagRepository.existsByNormalizedName(normalized)) {
            throw new DuplicateMasterDataException("Tag already exists: " + normalized);
        }
        var entity = new TagEntity(UUID.randomUUID(), displayName, normalized, Instant.now());
        tagRepository.save(entity);
        versionService.bumpVersion();
        return toView(entity);
    }

    @Transactional
    public TagView setActive(UUID id, boolean active) {
        var tag = tagRepository.findById(id).orElseThrow(() -> new TagNotFoundException(id));
        tag.setActive(active);
        versionService.bumpVersion();
        return toView(tag);
    }

    private TagView toView(TagEntity entity) {
        return new TagView(
                entity.id(), entity.displayName(), entity.normalizedName(), entity.active(), entity.createdAt());
    }

    private <T> MasterDataPageView<T> toPage(Page<T> page) {
        return new MasterDataPageView<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
