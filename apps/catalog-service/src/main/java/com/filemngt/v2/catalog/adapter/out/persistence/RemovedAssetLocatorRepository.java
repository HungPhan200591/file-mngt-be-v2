package com.filemngt.v2.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RemovedAssetLocatorRepository
        extends JpaRepository<RemovedAssetLocatorEntity, RemovedAssetLocatorEntity.Key> {}
