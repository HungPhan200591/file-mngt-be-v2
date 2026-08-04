package com.filemngt.v2.catalog.masterdata.application;

import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.MasterDataRegistryEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.MasterDataRegistryRepository;
import org.springframework.stereotype.Service;

/**
 * Đọc và bump registryVersion trong cùng transaction với caller.
 * Caller chịu trách nhiệm mở @Transactional trước khi gọi bumpVersion().
 */
@Service
public class MasterDataVersionService {

    private final MasterDataRegistryRepository registryRepository;

    public MasterDataVersionService(MasterDataRegistryRepository registryRepository) {
        this.registryRepository = registryRepository;
    }

    public long bumpVersion() {
        MasterDataRegistryEntity registry = registryRepository
                .findForUpdate()
                .orElseThrow(() -> new IllegalStateException("master_data_registry singleton not found"));
        registry.incrementVersion();
        return registry.version();
    }

    public long currentVersion() {
        return registryRepository
                .findById(1)
                .map(MasterDataRegistryEntity::version)
                .orElseThrow(() -> new IllegalStateException("master_data_registry singleton not found"));
    }
}
