package com.filemngt.v2.scan.domain;

import java.util.List;

/** Snapshot Catalog được chốt tại thời điểm bắt đầu scan để mọi file trong run dùng cùng một registry. */
public record ScanRegistrySnapshot(long registryVersion, String region, List<String> studioCodes, List<String> tags) {

    public ScanRegistrySnapshot {
        studioCodes = List.copyOf(studioCodes);
        tags = List.copyOf(tags);
    }
}
