package com.filemngt.v2.catalog.masterdata.application.exception;

import com.filemngt.v2.catalog.masterdata.application.dto.ImportReportView.ImportConflictItem;
import java.util.List;

public class ImportConflictException extends RuntimeException {

    private final List<ImportConflictItem> conflicts;

    public ImportConflictException(List<ImportConflictItem> conflicts) {
        super("Studio import has " + conflicts.size() + " conflict(s)");
        this.conflicts = conflicts;
    }

    public List<ImportConflictItem> conflicts() {
        return conflicts;
    }
}
