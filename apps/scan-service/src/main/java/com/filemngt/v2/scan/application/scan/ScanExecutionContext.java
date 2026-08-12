package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import java.util.UUID;

/** Context bất biến của một scan run execution, dùng chung giữa executor và parallel analyzer. */
record ScanExecutionContext(
        UUID runId,
        String workerId,
        ScanProperties.Root root,
        ScanRegistrySnapshot snapshot,
        boolean overwriteExisting) {}
