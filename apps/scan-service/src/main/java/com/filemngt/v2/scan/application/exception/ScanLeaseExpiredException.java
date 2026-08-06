package com.filemngt.v2.scan.application.exception;

import java.util.UUID;

/** Báo worker đã bị mất lease trong quá trình scan run, khiến chunk commit bị hủy bỏ. */
public class ScanLeaseExpiredException extends RuntimeException {
    public ScanLeaseExpiredException(UUID runId, String workerId) {
        super("Scan lease expired or lost for runId=" + runId + ", workerId=" + workerId);
    }
}
