package com.filemngt.v2.catalog.masterdata.application.dto;

import java.util.List;

public record ImportReportView(
        boolean dryRun,
        int totalInput,
        int createdCount,
        int mergedCount,
        int conflictCount,
        List<ImportConflictItem> conflicts) {

    public record ImportConflictItem(String region, String normalizedCode, List<String> conflictingStudios) {}
}
