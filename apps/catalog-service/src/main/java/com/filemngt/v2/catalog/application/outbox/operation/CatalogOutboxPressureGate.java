package com.filemngt.v2.catalog.application.outbox.operation;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.operation.finalizer-enabled", havingValue = "true")
/** Control-plane sample có cap; finalizer dừng tạo output mới khi relay backlog vượt hysteresis. */
public class CatalogOutboxPressureGate {
    private final JdbcTemplate jdbc;
    private final int pauseThreshold;
    private final int resumeThreshold;
    private final AtomicBoolean paused = new AtomicBoolean();

    public CatalogOutboxPressureGate(
            JdbcTemplate jdbc,
            @Value("${catalog.outbox.operation-relay.pause-threshold:100000}") int pauseThreshold,
            @Value("${catalog.outbox.operation-relay.resume-threshold:50000}") int resumeThreshold) {
        if (resumeThreshold < 0 || pauseThreshold < 1 || resumeThreshold >= pauseThreshold) {
            throw new IllegalArgumentException("Catalog outbox pressure hysteresis is invalid");
        }
        this.jdbc = jdbc;
        this.pauseThreshold = pauseThreshold;
        this.resumeThreshold = resumeThreshold;
    }

    @Scheduled(fixedDelayString = "${catalog.outbox.operation-relay.pressure-sample-ms:1000}")
    public void sample() {
        int sampleLimit = paused.get() ? resumeThreshold + 1 : pauseThreshold + 1;
        Integer pending = jdbc.queryForObject(
                "select count(*) from (select 1 from catalog_outbox_event where published_at is null limit ?) sample",
                Integer.class,
                sampleLimit);
        int value = pending == null ? 0 : pending;
        if (paused.get()) paused.set(value > resumeThreshold);
        else paused.set(value > pauseThreshold);
    }

    public boolean isPaused() {
        return paused.get();
    }
}
