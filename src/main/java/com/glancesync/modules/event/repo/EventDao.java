package com.glancesync.modules.event.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.event.entity.Event;
import jakarta.inject.Singleton;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Singleton
public class EventDao {

    private static final String SELECT =
            "SELECT e.id, e.organizer_id, o.username AS organizer_username, "
                    + "e.invitee_id, i.username AS invitee_username, "
                    + "e.title, e.location, e.starts_at, e.status, e.created_at "
                    + "FROM events e "
                    + "JOIN users o ON o.id = e.organizer_id "
                    + "JOIN users i ON i.id = e.invitee_id ";

    private final Database database;

    public EventDao(Database database) {
        this.database = database;
    }

    public UUID insert(UUID organizerId, UUID inviteeId, String title, String location, Instant startsAt) {
        return database.insertReturningId(
                "INSERT INTO events (organizer_id, invitee_id, title, location, starts_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                organizerId, inviteeId, title, location, Timestamp.from(startsAt));
    }

    public Optional<Event> findById(UUID id) {
        return database.queryOne(SELECT + "WHERE e.id = ?", EventDao::map, id);
    }

    /** Every event the user is part of, soonest first. */
    public List<Event> findForUser(UUID userId) {
        return database.query(
                SELECT + "WHERE e.organizer_id = ? OR e.invitee_id = ? ORDER BY e.starts_at",
                EventDao::map,
                userId, userId);
    }

    public int updateStatus(UUID eventId, String status) {
        return database.update("UPDATE events SET status = ? WHERE id = ?", status, eventId);
    }

    private static Event map(ResultSet rs) throws SQLException {
        return new Event(
                rs.getObject("id", UUID.class),
                rs.getObject("organizer_id", UUID.class),
                rs.getString("organizer_username"),
                rs.getObject("invitee_id", UUID.class),
                rs.getString("invitee_username"),
                rs.getString("title"),
                rs.getString("location"),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }
}
