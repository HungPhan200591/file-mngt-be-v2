package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import com.filemngt.v2.contracts.events.MediaFileRemovedV1;

/** Port serialize event trước khi persistence adapter lưu payload JSON vào transactional outbox. */
public interface OutboxEventSerializer {
    /** Serialize media discovery event thành payload có thể publish lại về sau. */
    String serialize(MediaFileDiscoveredV2 event);

    String serialize(MediaFileRemovedV1 event);
}
