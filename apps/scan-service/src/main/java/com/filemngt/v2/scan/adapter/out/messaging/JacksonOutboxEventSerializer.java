package com.filemngt.v2.scan.adapter.out.messaging;

import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import com.filemngt.v2.scan.application.outbox.OutboxEventSerializer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
/** Adapter JSON cho port serialize outbox event, tách Jackson khỏi application use case. */
public class JacksonOutboxEventSerializer implements OutboxEventSerializer {
    private final ObjectMapper objectMapper;

    public JacksonOutboxEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    /** Serialize event đã hoàn chỉnh trước khi lưu cùng transaction quyết định. */
    public String serialize(MediaFileDiscoveredV2 event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize discovered media event", exception);
        }
    }
}
