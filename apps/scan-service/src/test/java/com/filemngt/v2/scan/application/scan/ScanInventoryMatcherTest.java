package com.filemngt.v2.scan.application.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.inventory.ScanInventorySnapshot;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScanInventoryMatcherTest {
    private static final String PATH = "sub/Joke [JOKE-001].mp4";

    private final ScanInventoryMatcher matcher = new ScanInventoryMatcher();

    @Test
    void treatsPostgresMicrosecondRoundingAsUnchanged() {
        var diskItem = item("2026-08-07T09:48:54.202999900Z");
        var snapshot = snapshot("2026-08-07T09:48:54.203000Z");

        var result = matcher.classify(diskItem, Map.of(PATH, snapshot));

        assertThat(result).isInstanceOf(ScanInventoryMatcher.MatchResult.Unchanged.class);
    }

    @Test
    void detectsRealTimestampChangeAfterNormalization() {
        var diskItem = item("2026-08-07T09:48:54.202998900Z");
        var snapshot = snapshot("2026-08-07T09:48:54.203000Z");

        var result = matcher.classify(diskItem, Map.of(PATH, snapshot));

        assertThat(result).isInstanceOf(ScanInventoryMatcher.MatchResult.NewOrChanged.class);
    }

    @Test
    void treatsMissingEntryAsChangedWhenItReappears() {
        var diskItem = item("2026-08-07T09:48:54.203000Z");
        var snapshot = snapshot("2026-08-07T09:48:54.203000Z", ScanFileInventoryState.MISSING);

        var result = matcher.classify(diskItem, Map.of(PATH, snapshot));

        assertThat(result).isInstanceOf(ScanInventoryMatcher.MatchResult.NewOrChanged.class);
    }

    private ScanInventoryItem item(String modifiedAt) {
        return new ScanInventoryItem("root", PATH, 0L, Instant.parse(modifiedAt));
    }

    private ScanInventorySnapshot snapshot(String modifiedAt) {
        return snapshot(modifiedAt, ScanFileInventoryState.PRESENT);
    }

    private ScanInventorySnapshot snapshot(String modifiedAt, ScanFileInventoryState state) {
        return new ScanInventorySnapshot(PATH, 0L, Instant.parse(modifiedAt), state);
    }
}
