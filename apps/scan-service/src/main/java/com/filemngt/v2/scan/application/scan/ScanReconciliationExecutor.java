package com.filemngt.v2.scan.application.scan;

import org.springframework.stereotype.Component;

/** Điều phối reconciliation theo mode sequential hoặc pipelined đã cấu hình. */
@Component
public class ScanReconciliationExecutor {
    private final ScanReconciliationPipeline pipeline;

    public ScanReconciliationExecutor(ScanReconciliationPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public void reconcile(ScanReconciliationRequest request) {
        pipeline.execute(request);
    }
}
