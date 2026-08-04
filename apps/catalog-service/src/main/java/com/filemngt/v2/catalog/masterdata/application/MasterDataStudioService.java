package com.filemngt.v2.catalog.masterdata.application;

import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioCodeEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioCodeRepository;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioRepository;
import com.filemngt.v2.catalog.masterdata.application.dto.MasterDataPageView;
import com.filemngt.v2.catalog.masterdata.application.dto.StudioCodeView;
import com.filemngt.v2.catalog.masterdata.application.dto.StudioView;
import com.filemngt.v2.catalog.masterdata.application.exception.DuplicateMasterDataException;
import com.filemngt.v2.catalog.masterdata.application.exception.StudioCodeNotFoundException;
import com.filemngt.v2.catalog.masterdata.application.exception.StudioNotFoundException;
import com.filemngt.v2.catalog.masterdata.domain.MasterDataNormalizer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataStudioService {

    private final StudioRepository studioRepository;
    private final StudioCodeRepository codeRepository;
    private final MasterDataVersionService versionService;

    public MasterDataStudioService(
            StudioRepository studioRepository,
            StudioCodeRepository codeRepository,
            MasterDataVersionService versionService) {
        this.studioRepository = studioRepository;
        this.codeRepository = codeRepository;
        this.versionService = versionService;
    }

    @Transactional(readOnly = true)
    public MasterDataPageView<StudioView> list(String region, String name, Boolean active, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("displayName"));
        Page<StudioEntity> result;
        if (region != null && name != null && active != null) {
            result = studioRepository.findByRegionAndActiveAndDisplayNameContainingIgnoreCase(
                    region, active, name, pageable);
        } else if (region != null && name != null) {
            result = studioRepository.findByRegionAndDisplayNameContainingIgnoreCase(region, name, pageable);
        } else if (region != null && active != null) {
            result = studioRepository.findByRegionAndActive(region, active, pageable);
        } else if (name != null && active != null) {
            result = studioRepository.findByActiveAndDisplayNameContainingIgnoreCase(active, name, pageable);
        } else if (region != null) {
            result = studioRepository.findByRegion(region, pageable);
        } else if (active != null) {
            result = studioRepository.findByActive(active, pageable);
        } else if (name != null) {
            result = studioRepository.findByDisplayNameContainingIgnoreCase(name, pageable);
        } else {
            result = studioRepository.findAll(pageable);
        }
        return toPage(result.map(e -> {
            var codes = codeRepository.findByStudioId(e.id()).stream()
                    .map(this::toCodeView)
                    .toList();
            return toView(e, codes);
        }));
    }

    @Transactional(readOnly = true)
    public StudioView get(UUID id) {
        var studio = findStudio(id);
        var codes =
                codeRepository.findByStudioId(id).stream().map(this::toCodeView).toList();
        return toView(studio, codes);
    }

    @Transactional
    public StudioView create(String region, String displayName) {
        var normalized = MasterDataNormalizer.normalizeName(displayName);
        if (studioRepository.existsByRegionAndNormalizedName(region, normalized)) {
            throw new DuplicateMasterDataException("Studio already exists: " + region + "/" + normalized);
        }
        var entity = new StudioEntity(UUID.randomUUID(), region, displayName, normalized, Instant.now());
        studioRepository.save(entity);
        versionService.bumpVersion();
        return toView(entity, List.of());
    }

    @Transactional
    public StudioView update(UUID id, String displayName) {
        var studio = findStudio(id);
        studio.setDisplayName(displayName);
        versionService.bumpVersion();
        var codes =
                codeRepository.findByStudioId(id).stream().map(this::toCodeView).toList();
        return toView(studio, codes);
    }

    @Transactional
    public StudioView setActive(UUID id, boolean active) {
        var studio = findStudio(id);
        studio.setActive(active);
        versionService.bumpVersion();
        var codes =
                codeRepository.findByStudioId(id).stream().map(this::toCodeView).toList();
        return toView(studio, codes);
    }

    // ── Studio Code ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StudioCodeView> listCodes(UUID studioId, Boolean active) {
        findStudio(studioId);
        var codes = active != null
                ? codeRepository.findByStudioIdAndActive(studioId, active)
                : codeRepository.findByStudioId(studioId);
        return codes.stream().map(this::toCodeView).toList();
    }

    @Transactional
    public StudioCodeView addCode(UUID studioId, String rawCode) {
        var studio = findStudio(studioId);
        var normalized = MasterDataNormalizer.normalizeCode(rawCode);
        if (codeRepository.existsByRegionAndNormalizedCode(studio.region(), normalized)) {
            throw new DuplicateMasterDataException("Studio code already exists: " + studio.region() + "/" + normalized);
        }
        var entity = new StudioCodeEntity(UUID.randomUUID(), studio, rawCode, normalized, Instant.now());
        codeRepository.save(entity);
        versionService.bumpVersion();
        return toCodeView(entity);
    }

    @Transactional
    public void removeCode(UUID studioId, UUID codeId) {
        findStudio(studioId);
        var code = codeRepository.findById(codeId).orElseThrow(() -> new StudioCodeNotFoundException(codeId));
        codeRepository.delete(code);
        versionService.bumpVersion();
    }

    @Transactional
    public StudioCodeView setCodeActive(UUID studioId, UUID codeId, boolean active) {
        findStudio(studioId);
        var code = codeRepository.findById(codeId).orElseThrow(() -> new StudioCodeNotFoundException(codeId));
        code.setActive(active);
        versionService.bumpVersion();
        return toCodeView(code);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private StudioEntity findStudio(UUID id) {
        return studioRepository.findById(id).orElseThrow(() -> new StudioNotFoundException(id));
    }

    private StudioView toView(StudioEntity entity, List<StudioCodeView> codes) {
        return new StudioView(
                entity.id(),
                entity.region(),
                entity.displayName(),
                entity.normalizedName(),
                entity.active(),
                entity.createdAt(),
                codes);
    }

    private StudioCodeView toCodeView(StudioCodeEntity entity) {
        return new StudioCodeView(
                entity.id(),
                entity.studio().id(),
                entity.region(),
                entity.rawCode(),
                entity.normalizedCode(),
                entity.active(),
                entity.createdAt());
    }

    private <T> MasterDataPageView<T> toPage(Page<T> page) {
        return new MasterDataPageView<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
