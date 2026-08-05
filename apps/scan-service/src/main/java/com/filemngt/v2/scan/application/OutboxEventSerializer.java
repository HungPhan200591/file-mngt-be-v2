package com.filemngt.v2.scan.application;

import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;

@FunctionalInterface
/** Port serialize event trước khi persistence adapter lưu payload JSON vào transactional outbox. */
public interface OutboxEventSerializer {
    /** Serialize media discovery event thành payload có thể publish lại về sau. */
    String serialize(MediaFileDiscoveredV2 event);
}
