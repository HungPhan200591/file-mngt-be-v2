package com.filemngt.v2.catalog.masterdata.application;

import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.ActressEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.ActressRepository;
import com.filemngt.v2.catalog.masterdata.application.dto.ActressView;
import com.filemngt.v2.catalog.masterdata.application.dto.MasterDataPageView;
import com.filemngt.v2.catalog.masterdata.application.exception.ActressNotFoundException;
import com.filemngt.v2.catalog.masterdata.application.exception.DuplicateMasterDataException;
import com.filemngt.v2.catalog.masterdata.domain.MasterDataNormalizer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataActressService {

    private final ActressRepository actressRepository;
    private final MasterDataVersionService versionService;

    public MasterDataActressService(ActressRepository actressRepository, MasterDataVersionService versionService) {
        this.actressRepository = actressRepository;
        this.versionService = versionService;
    }

    @Transactional(readOnly = true)
    public MasterDataPageView<ActressView> list(String region, String name, Boolean active, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("displayName"));
        Page<ActressEntity> result;
        if (region != null && name != null && active != null) {
            result = actressRepository.findByRegionAndActiveAndDisplayNameContainingIgnoreCase(
                    region, active, name, pageable);
        } else if (region != null && name != null) {
            result = actressRepository.findByRegionAndDisplayNameContainingIgnoreCase(region, name, pageable);
        } else if (region != null && active != null) {
            result = actressRepository.findByRegionAndActive(region, active, pageable);
        } else if (name != null && active != null) {
            result = actressRepository.findByActiveAndDisplayNameContainingIgnoreCase(active, name, pageable);
        } else if (region != null) {
            result = actressRepository.findByRegion(region, pageable);
        } else if (active != null) {
            result = actressRepository.findByActive(active, pageable);
        } else if (name != null) {
            result = actressRepository.findByDisplayNameContainingIgnoreCase(name, pageable);
        } else {
            result = actressRepository.findAll(pageable);
        }
        return toPage(result.map(this::toView));
    }

    @Transactional
    public ActressView create(String region, String displayName) {
        var normalized = MasterDataNormalizer.normalizeName(displayName);
        if (actressRepository.existsByRegionAndNormalizedName(region, normalized)) {
            throw new DuplicateMasterDataException("Actress already exists: " + region + "/" + normalized);
        }
        var entity = new ActressEntity(UUID.randomUUID(), region, displayName, normalized, Instant.now());
        actressRepository.save(entity);
        versionService.bumpVersion();
        return toView(entity);
    }

    @Transactional
    public ActressView setActive(UUID id, boolean active) {
        var actress = actressRepository.findById(id).orElseThrow(() -> new ActressNotFoundException(id));
        actress.setActive(active);
        versionService.bumpVersion();
        return toView(actress);
    }

    private ActressView toView(ActressEntity entity) {
        return new ActressView(
                entity.id(),
                entity.region(),
                entity.displayName(),
                entity.normalizedName(),
                entity.active(),
                entity.createdAt());
    }

    private <T> MasterDataPageView<T> toPage(Page<T> page) {
        return new MasterDataPageView<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
