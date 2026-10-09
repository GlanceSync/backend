package com.glancesync.modules.notification.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.notification.entity.Notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class NotificationDao {

    private static final String SELECT =
            "SELECT n.id, n.type, n.actor_id, u.username AS actor_username, "
                    + "n.body, n.created_at, n.read_at "
                    + "FROM notifications n LEFT JOIN users u ON u.id = n.actor_id ";

    private final Database database;

    public NotificationDao(Database database) {
        this.database = database;
    }

    public UUID insert(UUID userId, String type, UUID actorId, String body) {
        return database.insertReturningId(
                "INSERT INTO notifications (user_id, type, actor_id, body) VALUES (?, ?, ?, ?)",
                userId, type, actorId, body);
    }

    public Optional<Notification> findById(UUID id) {
        return database.queryOne(SELECT + "WHERE n.id = ?", NotificationDao::map, id);
    }

    public List<Notification> recentFor(UUID userId, int limit) {
        return database.query(
                SELECT + "WHERE n.user_id = ? ORDER BY n.created_at DESC, n.id DESC LIMIT ?",
                NotificationDao::map,
                userId, limit);
    }

    public int unreadCount(UUID userId) {
        return database.queryOne(
                "SELECT COUNT(*) AS unread FROM notifications WHERE user_id = ? AND read_at IS NULL",
                rs -> rs.getInt("unread"),
                userId).orElse(0);
    }

    public int markAllRead(UUID userId) {
        return database.update(
                "UPDATE notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL",
                userId);
    }

    private static Notification map(ResultSet rs) throws SQLException {
        UUID actorId = rs.getObject("actor_id", UUID.class);
        return new Notification(
                rs.getObject("id", UUID.class),
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
