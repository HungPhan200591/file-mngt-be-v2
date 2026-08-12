// package com.filemngt.v2.scan.application.scan;
//
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
//
// import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
// import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
// import com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationPageReader;
// import com.filemngt.v2.scan.config.ScanProperties;
// import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
// import com.filemngt.v2.scan.domain.scan.ScanProfile;
// import java.util.List;
// import java.util.Optional;
// import java.util.UUID;
// import org.junit.jupiter.api.Test;
//
// class ScanExecutorTest {
//    @Test
//    void storesSafeFailureWhenRootDisappearsBeforeFilesystemWalk() {
//        var runs = mock(ScanRunRepository.class);
//        var run = mock(ScanRunEntity.class);
//        var committer = mock(ScanChunkCommitter.class);
//        var runId = UUID.randomUUID();
//        when(runs.findById(runId)).thenReturn(Optional.of(run));
//        var failureHandler = new ScanExecutionFailureHandler(runs, committer);
//        var liveness = mock(ScanExecutionLiveness.class);
//
//        var executor = new ScanExecutor(
//                runs,
//                committer,
//                mock(ScanFileAnalyzer.class),
//                mock(ScanReconciliationPageReader.class),
//                mock(ScanProperties.class),
//                failureHandler,
//                liveness);
//        var root = new ScanProperties.Root("missing-root", "Z:/path-that-does-not-exist", ScanProfile.JOKE_VIDEO);
//        var snapshot = new ScanRegistrySnapshot(1L, "JOKE", List.of(), List.of());
//
//        executor.execute(runId, root, snapshot);
//
//        verify(run).fail("Configured scan root became unavailable during execution: missing-root");
//        verify(runs).saveAndFlush(run);
//        verify(committer).cleanupStage(runId);
//    }
// }
