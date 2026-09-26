package com.glancesync.call;

import com.glancesync.db.Database;
import jakarta.inject.Singleton;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Singleton
public class CallDao {

    private static final String COLUMNS =
            "id, caller_id, callee_id, mode, status, started_at, answered_at, ended_at, duration_seconds";

    private final Database database;

    public CallDao(Database database) {
        this.database = database;
    }

    public long insert(long callerId, long calleeId, String mode, String status,
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

    public Optional<CallRecord> findById(long id) {
        return database.queryOne("SELECT " + COLUMNS + " FROM calls WHERE id = ?", CallDao::map, id);
    }

    /** The calls between two people, oldest first. */
    public List<CallRecord> between(long userA, long userB, int limit) {
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
                rs.getLong("id"),
                rs.getLong("caller_id"),
                rs.getLong("callee_id"),
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
