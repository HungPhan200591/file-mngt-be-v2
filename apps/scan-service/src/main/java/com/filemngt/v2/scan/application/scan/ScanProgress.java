package com.filemngt.v2.scan.application.scan;

/** Bộ đếm mutable, bounded-memory của một scan run đang được executor xử lý. */
final class ScanProgress {
    private long files;
    private long proposals;
    private long issues;
    private long skipped;

    void recordFile() {
        files++;
    }

    void recordSkipped() {
        skipped++;
    }

    void recordResult(ScanFileAnalyzer.Result result) {
        switch (result) {
            case ScanFileAnalyzer.Proposal ignored -> proposals++;
            case ScanFileAnalyzer.Issue ignored -> issues++;
        }
    }

    long files() {
        return files;
    }

    long proposals() {
        return proposals;
    }

    long issues() {
        return issues;
    }

    long skipped() {
        return skipped;
    }
}
