package com.filemngt.v2.scan.domain.registry;

import com.filemngt.v2.scan.domain.scan.ScanProfile;

/** Vùng Catalog có bộ quy ước metadata riêng. */
public enum ScanRegion {
    JOKE,
    USE;

    /** Ánh xạ profile scan sang vùng registry sở hữu quy ước tương ứng. */
    public static ScanRegion from(ScanProfile profile) {
        return switch (profile) {
            case JOKE_VIDEO, JOKE_ASSET -> JOKE;
            case USE_VIDEO, USE_ASSET, USE_ALBUM -> USE;
        };
    }
}
