package com.filemngt.v2.scan.adapter.out.catalog;

import java.util.List;

/**
 * Immutable snapshot registry nhận từ catalog-service trước khi tạo scan_run.
 * Chứa active studio codes của region yêu cầu và global active tags.
 */
public record RegistrySnapshot(long registryVersion, String region, List<String> studioCodes, List<String> tags) {

    public RegistrySnapshot {
        studioCodes = List.copyOf(studioCodes);
        tags = List.copyOf(tags);
    }
}
