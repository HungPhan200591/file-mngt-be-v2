package com.filemngt.v2.catalog.adapter.out.persistence.outbox.operation;

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
/** Lane lease/fence là control plane; fetch và set-based mark là compact native data plane. */
public class CatalogOutboxRelayLaneStore {
    private final JdbcTemplate jdbc;

    public CatalogOutboxRelayLaneStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<CatalogOutboxRelayLaneClaim> acquire(int laneId, String owner, Instant now, Instant leaseUntil) {
        var claims = jdbc.query(
                """
                with candidate as (
                    select lane_id from catalog_outbox_relay_lane
                    where lane_id = ? and (lease_until is null or lease_until < ?)
                    for update skip locked
                )
                update catalog_outbox_relay_lane lane
                set lease_owner = ?, lease_until = ?, fence_token = lane.fence_token + 1,
                    last_heartbeat_at = ?
                from candidate where lane.lane_id = candidate.lane_id
                returning lane.lane_id, lane.lease_owner, lane.lease_until, lane.fence_token
                """,
                (result, row) -> new CatalogOutboxRelayLaneClaim(
                        result.getInt("lane_id"),
                        result.getString("lease_owner"),
                        result.getTimestamp("lease_until").toInstant(),
                        result.getLong("fence_token")),
                laneId,
                Timestamp.from(now),
                owner,
                Timestamp.from(leaseUntil),
                Timestamp.from(now));
        return claims.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<CatalogOutboxRelayRecord> fetchPending(int laneId, int limit) {
        return jdbc.query(
                """
                select id, event_type, partition_key, payload, correlation_id, traceparent
                from catalog_outbox_event
                where published_at is null
                  and relay_lane_id = ?
                order by created_at, id limit ?
                """,
                (result, row) -> new CatalogOutboxRelayRecord(
                        result.getObject("id", UUID.class),
                        result.getString("event_type"),
                        result.getString("partition_key"),
                        result.getString("payload"),
                        result.getString("correlation_id"),
                        result.getString("traceparent")),
                laneId,
                limit);
    }

    public int markPublished(List<UUID> eventIds, CatalogOutboxRelayLaneClaim claim, Instant now) {
        if (eventIds.isEmpty()) return 0;
        return jdbc.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    update catalog_outbox_event event set published_at = ?, last_error = null
                    from catalog_outbox_relay_lane lane
                    where event.id = any (?::uuid[]) and event.published_at is null
                      and event.relay_lane_id = ?
                      and lane.lane_id = ? and lane.lease_owner = ? and lane.fence_token = ?
                      and lane.lease_until > ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                Array ids = connection.createArrayOf("uuid", eventIds.toArray(UUID[]::new));
                statement.setArray(2, ids);
                statement.setInt(3, claim.laneId());
                statement.setInt(4, claim.laneId());
                statement.setString(5, claim.owner());
                statement.setLong(6, claim.fenceToken());
                statement.setTimestamp(7, Timestamp.from(now));
                return statement.executeUpdate();
            }
        });
    }

    public int markFailed(UUID eventId, CatalogOutboxRelayLaneClaim claim, String error, Instant now) {
        return jdbc.update(
                """
                update catalog_outbox_event event
                set attempt_count = attempt_count + 1, last_error = ?
                from catalog_outbox_relay_lane lane
                where event.id = ? and event.published_at is null
                  and event.relay_lane_id = ?
                  and lane.lane_id = ? and lane.lease_owner = ? and lane.fence_token = ? and lane.lease_until > ?
                """,
                error,
                eventId,
                claim.laneId(),
                claim.laneId(),
                claim.owner(),
                claim.fenceToken(),
                Timestamp.from(now));
    }

    public void release(CatalogOutboxRelayLaneClaim claim) {
        jdbc.update("""
                update catalog_outbox_relay_lane set lease_owner = null, lease_until = null,
                    last_heartbeat_at = now()
                where lane_id = ? and lease_owner = ? and fence_token = ?
                """, claim.laneId(), claim.owner(), claim.fenceToken());
    }
}
