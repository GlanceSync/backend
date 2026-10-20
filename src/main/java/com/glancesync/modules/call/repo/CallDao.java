package com.glancesync.modules.call.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.call.entity.CallRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class CallDao {

    private static final String COLUMNS =
            "id, caller_id, callee_id, mode, status, started_at, answered_at, ended_at, duration_seconds";

    private final Database database;

    public CallDao(Database database) {
        this.database = database;
    }

    public UUID insert(UUID callerId, UUID calleeId, String mode, String status,
                       Instant startedAt, Instant answeredAt, Instant endedAt, Integer durationSeconds) {
        return database.insertReturningId(
                "INSERT INTO calls (caller_id, callee_id, mode, status, started_at, answered_at, "
                        + "ended_at, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                callerId, calleeId, mode, status,
                Timestamp.from(startedAt),
                answeredAt == null ? null : Timestamp.from(answeredAt),
                Timestamp.from(endedAt),
                durationSeconds);
    }

    public Optional<CallRecord> findById(UUID id) {
        return database.queryOne("SELECT " + COLUMNS + " FROM calls WHERE id = ?", CallDao::map, id);
    }

    /** The calls between two people, oldest first. */
    public List<CallRecord> between(UUID userA, UUID userB, int limit) {
        return database.query(
                "SELECT " + COLUMNS + " FROM calls "
                        + "WHERE (caller_id = ? AND callee_id = ?) OR (caller_id = ? AND callee_id = ?) "
                        + "ORDER BY started_at DESC, id DESC LIMIT ?",
                CallDao::map,
                userA, userB, userB, userA, limit);
    }

    private static CallRecord map(ResultSet rs) throws SQLException {
        int duration = rs.getInt("duration_seconds");
        // wasNull() reports on the column just read, so it is checked immediately.
        Integer durationSeconds = rs.wasNull() ? null : duration;
        return new CallRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("caller_id", UUID.class),
                rs.getObject("callee_id", UUID.class),
                rs.getString("mode"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("answered_at")),
                toInstant(rs.getTimestamp("ended_at")),
                durationSeconds);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
