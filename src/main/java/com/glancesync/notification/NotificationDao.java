package com.glancesync.notification;

import com.glancesync.db.Database;
import jakarta.inject.Singleton;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Singleton
public class NotificationDao {

    private static final String SELECT =
            "SELECT n.id, n.type, n.actor_id, u.username AS actor_username, "
                    + "n.body, n.created_at, n.read_at "
                    + "FROM notifications n LEFT JOIN users u ON u.id = n.actor_id ";

    private final Database database;

    public NotificationDao(Database database) {
        this.database = database;
    }

    public long insert(long userId, String type, Long actorId, String body) {
        return database.insertReturningId(
                "INSERT INTO notifications (user_id, type, actor_id, body) VALUES (?, ?, ?, ?)",
                userId, type, actorId, body);
    }

    public Optional<Notification> findById(long id) {
        return database.queryOne(SELECT + "WHERE n.id = ?", NotificationDao::map, id);
    }

    public List<Notification> recentFor(long userId, int limit) {
        return database.query(
                SELECT + "WHERE n.user_id = ? ORDER BY n.created_at DESC, n.id DESC LIMIT ?",
                NotificationDao::map,
                userId, limit);
    }

    public int unreadCount(long userId) {
        return database.queryOne(
                "SELECT COUNT(*) AS unread FROM notifications WHERE user_id = ? AND read_at IS NULL",
                rs -> rs.getInt("unread"),
                userId).orElse(0);
    }

    public int markAllRead(long userId) {
        return database.update(
                "UPDATE notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL",
                userId);
    }

    private static Notification map(ResultSet rs) throws SQLException {
        long actorIdValue = rs.getLong("actor_id");
        // wasNull() reports on the column just read, so it must be checked immediately.
        Long actorId = rs.wasNull() ? null : actorIdValue;
        return new Notification(
                rs.getLong("id"),
                rs.getString("type"),
                actorId,
                rs.getString("actor_username"),
                rs.getString("body"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("read_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
