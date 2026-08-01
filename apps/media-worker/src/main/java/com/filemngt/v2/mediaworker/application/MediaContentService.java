package com.filemngt.v2.mediaworker.application;

import com.filemngt.v2.mediaworker.adapter.out.catalog.CatalogAssetClient;
import com.filemngt.v2.mediaworker.adapter.out.filesystem.MediaRootResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MediaContentService {
    private final CatalogAssetClient catalog;
    private final MediaRootResolver roots;

    public MediaContentService(CatalogAssetClient catalog, MediaRootResolver roots) {
        this.catalog = catalog;
        this.roots = roots;
    }

    public MediaRootResolver.ResolvedMedia resolve(UUID subjectId, UUID assetId, String correlationId) {
        var asset = catalog.findAsset(subjectId, assetId, correlationId);
        return roots.resolve(asset.storageKey(), asset.relativePath());
    }
}
