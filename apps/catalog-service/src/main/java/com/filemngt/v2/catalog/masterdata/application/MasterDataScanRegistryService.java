package com.filemngt.v2.catalog.masterdata.application;

import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.StudioCodeRepository;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.TagRepository;
import com.filemngt.v2.catalog.masterdata.application.dto.RegistrySnapshotView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo snapshot registry cho scan-service trước khi bắt đầu một scan run.
 * Chỉ trả Studio Code active của region yêu cầu và Tag global active.
 * Read-only, không mutation.
 */
@Service
public class MasterDataScanRegistryService {

    private final StudioCodeRepository codeRepository;
    private final TagRepository tagRepository;
    private final MasterDataVersionService versionService;

    public MasterDataScanRegistryService(
            StudioCodeRepository codeRepository, TagRepository tagRepository, MasterDataVersionService versionService) {
        this.codeRepository = codeRepository;
        this.tagRepository = tagRepository;
        this.versionService = versionService;
    }

    @Transactional(readOnly = true)
    public RegistrySnapshotView snapshot(String region) {
        long version = versionService.currentVersion();
        var studioCodes = codeRepository.findByRegionAndActive(region, true).stream()
                .map(c -> c.normalizedCode())
                .sorted()
                .toList();
        var tags = tagRepository.findByActive(true).stream()
                .map(t -> t.normalizedName())
                .sorted()
                .toList();
        return new RegistrySnapshotView(version, region, studioCodes, tags);
    }
}
