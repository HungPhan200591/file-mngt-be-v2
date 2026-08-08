package com.filemngt.v2.scan.application.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ScanExecutionTimelineTest {
    @Test
    void capturesEndToEndDurationsAndCountersInTerminalSnapshot() {
        var clock = new AtomicLong(1_000_000_000L);
        var timeline = new ScanExecutionTimeline("corr-001", clock.get(), clock::get);
        var progress = new ScanProgress();
        UUID runId = UUID.fromString("018f4f8c-6a2b-7e2d-9a0b-76543210fedc");

        advance(clock, 10);
        timeline.accepted(runId);
        advance(clock, 5);
        timeline.workerStarted();
        advance(clock, 3);
        timeline.discoveryStarted();
        advance(clock, 7);
        timeline.discoveryCompleted();
        advance(clock, 2);
        timeline.diffStarted();
        advance(clock, 4);
        timeline.diffCompleted();
        advance(clock, 1);
        timeline.reconciliationStarted();
        advance(clock, 9);
        timeline.reconciliationCompleted();
        advance(clock, 2);
        timeline.finalizingStarted();

        progress.recordFiles(100);
        progress.setChangedFiles(20);
        progress.recordReconciledFiles(20);
        progress.recordResult(new ScanFileAnalyzer.Proposal(null));
        progress.recordResult(new ScanFileAnalyzer.Issue(null));
        progress.recordSkipped(80);
        advance(clock, 6);

        var snapshot = timeline.snapshot("completed", progress, null);

        assertThat(snapshot.runId()).isEqualTo(runId);
        assertThat(snapshot.correlationId()).isEqualTo("corr-001");
        assertThat(snapshot.totalDurationMs()).isEqualTo(49);
        assertThat(snapshot.httpAcceptedMs()).isEqualTo(10);
        assertThat(snapshot.queueWaitMs()).isEqualTo(5);
        assertThat(snapshot.discoveryMs()).isEqualTo(7);
        assertThat(snapshot.diffMs()).isEqualTo(4);
        assertThat(snapshot.reconciliationMs()).isEqualTo(9);
        assertThat(snapshot.finalizeMs()).isEqualTo(6);
        assertThat(snapshot.files()).isEqualTo(100);
        assertThat(snapshot.changedFiles()).isEqualTo(20);
        assertThat(snapshot.reconciledFiles()).isEqualTo(20);
        assertThat(snapshot.proposals()).isEqualTo(1);
        assertThat(snapshot.issues()).isEqualTo(1);
        assertThat(snapshot.skippedFiles()).isEqualTo(80);
    }

    @Test
    void leavesIncompletePhaseDurationEmptyWhenRunFailsEarly() {
        var clock = new AtomicLong(0);
        var timeline = new ScanExecutionTimeline("corr-002", clock.get(), clock::get);
        timeline.accepted(UUID.randomUUID());

        var snapshot = timeline.snapshot("failed", new ScanProgress(), "IllegalStateException");

        assertThat(snapshot.discoveryMs()).isNull();
        assertThat(snapshot.diffMs()).isNull();
        assertThat(snapshot.reconciliationMs()).isNull();
        assertThat(snapshot.finalizeMs()).isNull();
        assertThat(snapshot.errorType()).isEqualTo("IllegalStateException");
    }

    private void advance(AtomicLong clock, long millis) {
        clock.addAndGet(millis * 1_000_000L);
    }
}
