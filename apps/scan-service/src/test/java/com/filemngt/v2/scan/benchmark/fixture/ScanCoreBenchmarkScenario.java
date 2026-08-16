package com.filemngt.v2.scan.benchmark.fixture;

import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.List;

public enum ScanCoreBenchmarkScenario {
    COLD,
    UNCHANGED,
    INCREMENTAL,
    FULL_CHANGE,
    REVIVED;

    public Expectation expectation(List<ScanInventoryItem> items) {
        long changed = 0L;
        long issues = 0L;
        for (int index = 0; index < items.size(); index++) {
            if (isChanged(index + 1)) {
                changed++;
                if (items.get(index).sourceRelativePath().contains("Unformatted_Raw_Clip_NoCode")) {
                    issues++;
                }
            }
        }
        return new Expectation(changed, changed - issues, issues);
    }

    private boolean isChanged(int position) {
        return switch (this) {
            case COLD, FULL_CHANGE, REVIVED -> true;
            case UNCHANGED -> false;
            // Fixture mutate path issue kết thúc bằng "00.mp4"; issue rate 1% nên chọn mỗi 1.000 item.
            case INCREMENTAL -> position % 1_000 == 0;
        };
    }

    public record Expectation(long changedFiles, long proposals, long issues) {}
}
