package com.filemngt.v2.scan.adapter.out.persistence.outbox.lane;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
/** Native JDBC data plane: lease/fence ở lane ledger, còn event chỉ read rồi batch mark sau broker ack. */
public class ScanOutboxRelayLaneStore {
    private static final String ACQUIRE_LANE = """
            WITH candidate AS (
                SELECT lane_id
                FROM scan_outbox_relay_lane
                WHERE lane_id = ?
                  AND (lease_owner = ? OR lease_until IS NULL OR lease_until < ?)
                FOR UPDATE SKIP LOCKED
            )
            UPDATE scan_outbox_relay_lane lane
            SET lease_owner = ?, lease_until = ?, fence_token = lane.fence_token + 1,
                last_heartbeat_at = ?
            FROM candidate
            WHERE lane.lane_id = candidate.lane_id
            RETURNING lane.lane_id, lane.lease_owner, lane.lease_until, lane.fence_token
            """;
    private static final String FETCH_PENDING = """
            SELECT id, event_type, partition_key, payload, correlation_id, traceparent, created_at
            FROM scan_outbox_event
            WHERE published_at IS NULL
              AND (get_byte(decode(md5(partition_key), 'hex'), 0) & 63) = ?
            ORDER BY created_at, id
            LIMIT ?
            """;
    private static final String MARK_PUBLISHED = """
            UPDATE scan_outbox_event event
            SET published_at = ?, last_error = NULL
            FROM scan_outbox_relay_lane lane
            WHERE event.id = ANY (?::uuid[])
              AND event.published_at IS NULL
              AND lane.lane_id = ?
              AND lane.lease_owner = ?
              AND lane.fence_token = ?
              AND lane.lease_until > ?
            """;
    private static final String MARK_FAILED = """
            UPDATE scan_outbox_event event
            SET attempt_count = event.attempt_count + 1, last_error = ?
            FROM scan_outbox_relay_lane lane
            WHERE event.id = ?
              AND event.published_at IS NULL
              AND lane.lane_id = ?
              AND lane.lease_owner = ?
              AND lane.fence_token = ?
              AND lane.lease_until > ?
            """;

    private final JdbcTemplate jdbc;

    public ScanOutboxRelayLaneStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<OutboxRelayLaneClaim> acquire(int laneId, String owner, Instant now, Instant leaseUntil) {
        var claims = jdbc.query(
                ACQUIRE_LANE,
                (result, row) -> new OutboxRelayLaneClaim(
                        result.getInt("lane_id"),
                        result.getString("lease_owner"),
                        result.getTimestamp("lease_until").toInstant(),
                        result.getLong("fence_token")),
                laneId,
                owner,
                Timestamp.from(now),
                owner,
                Timestamp.from(leaseUntil),
                Timestamp.from(now));
        return claims.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<OutboxRelayRecord> fetchPending(int laneId, int limit) {
        return jdbc.query(
                FETCH_PENDING,
                (result, row) -> new OutboxRelayRecord(
                        result.getObject("id", UUID.class),
                        result.getString("event_type"),
                        result.getString("partition_key"),
                        result.getString("payload"),
                        result.getString("correlation_id"),
                        result.getString("traceparent"),
                        result.getTimestamp("created_at").toInstant()),
                laneId,
                limit);
    }

    public int markPublished(List<UUID> eventIds, OutboxRelayLaneClaim claim, Instant now) {
        if (eventIds.isEmpty()) return 0;
        return jdbc.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(MARK_PUBLISHED)) {
                statement.setTimestamp(1, Timestamp.from(now));
                Array ids = connection.createArrayOf("uuid", eventIds.toArray(UUID[]::new));
                statement.setArray(2, ids);
                statement.setInt(3, claim.laneId());
                statement.setString(4, claim.owner());
                statement.setLong(5, claim.fenceToken());
                statement.setTimestamp(6, Timestamp.from(now));
                return statement.executeUpdate();
            }
        });
    }

    public int markFailed(UUID eventId, OutboxRelayLaneClaim claim, String error, Instant now) {
        return jdbc.update(
                MARK_FAILED, error, eventId, claim.laneId(), claim.owner(), claim.fenceToken(), Timestamp.from(now));
    }
}
