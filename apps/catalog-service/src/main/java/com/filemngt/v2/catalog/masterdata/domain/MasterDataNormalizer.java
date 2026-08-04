package com.filemngt.v2.catalog.masterdata.domain;

import java.util.Locale;

/** Chuẩn hóa tên Studio, Tag, Actress và Studio Code thành dạng canonical. */
public final class MasterDataNormalizer {

    private MasterDataNormalizer() {}

    /**
     * Normalize display name: trim → collapse whitespace → uppercase (Locale.ROOT).
     * Dùng cho studio.normalized_name, actress.normalized_name, tag.normalized_name.
     */
    public static String normalizeName(String raw) {
        if (raw == null) return null;
        return raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /**
     * Normalize code: trim → remove all whitespace → uppercase (Locale.ROOT).
     * Dùng cho studio_code.normalized_code.
     */
    public static String normalizeCode(String raw) {
        if (raw == null) return null;
        return raw.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
