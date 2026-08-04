package com.filemngt.v2.catalog.masterdata.application.dto;

import java.util.List;

public record RegistrySnapshotView(long registryVersion, String region, List<String> studioCodes, List<String> tags) {}
