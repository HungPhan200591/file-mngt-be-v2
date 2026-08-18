package com.filemngt.v2.scan.adapter.out.persistence.outbox.lane;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.benchmark.fixture.OutboxDrainBenchmarkFixture;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        properties = {
            "scan.outbox.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false",
            "scan.approval-operation.enabled=false",
            "scan.review-projection.enabled=false"
        })
class ScanOutboxRelayLaneStoreIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ScanOutboxRelayLaneStore store;

    @BeforeEach
    void resetDatabase() {
        OutboxDrainBenchmarkFixture.reset(jdbc);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void fencesLateOwnerAndMarksOnlyItsLaneEvents() {
        OutboxDrainBenchmarkFixture.seedPendingOutbox(jdbc, 2);
        int laneId = jdbc.queryForObject("""
                SELECT get_byte(decode(md5(partition_key), 'hex'), 0) & 63
                FROM scan_outbox_event
                ORDER BY id
                LIMIT 1
                """, Integer.class);
        Instant now = Instant.now();
        var ownerA = store.acquire(laneId, "owner-a", now, now.plusSeconds(30)).orElseThrow();

        assertThat(store.acquire(laneId, "owner-b", now, now.plusSeconds(30))).isEmpty();

        var events = store.fetchPending(laneId, 100);
        int marked =
                store.markPublished(events.stream().map(OutboxRelayRecord::id).toList(), ownerA, now);

        assertThat(marked).isEqualTo(events.size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scan_outbox_event WHERE published_at IS NULL", Long.class))
                .isEqualTo(2L - events.size());
    }
}
