package com.glancesync.modules.visit.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.user.dto.ActivityResponse;
import com.glancesync.modules.user.entity.User;
import jakarta.inject.Singleton;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Singleton
public class ProfileViewDao {

    private final Database database;

    public ProfileViewDao(Database database) {
        this.database = database;
    }

    public Optional<Instant> record(UUID viewerId, UUID viewedId) {
        return database.queryOne(
                "WITH previous AS (SELECT last_viewed_at FROM profile_views "
                        + "                  WHERE viewer_id = ? AND viewed_id = ?) "
                        + "INSERT INTO profile_views (viewer_id, viewed_id) VALUES (?, ?) "
                        + "ON CONFLICT (viewer_id, viewed_id) DO UPDATE SET last_viewed_at = now() "
                        + "RETURNING (SELECT last_viewed_at FROM previous) AS previous",
                rs -> {
                    Timestamp previous = rs.getTimestamp("previous");
                    return previous == null ? null : previous.toInstant();
                },
                viewerId, viewedId, viewerId, viewedId);
    }

    public List<ActivityResponse> viewersOf(UUID userId) {
        return database.query(
                "SELECT u.id, u.username, u.profile_picture_id, v.last_viewed_at, "
                        + "(EXISTS (SELECT 1 FROM likes a WHERE a.liker_id = v.viewer_id AND a.liked_id = v.viewed_id) "
                        + " AND EXISTS (SELECT 1 FROM likes b WHERE b.liker_id = v.viewed_id AND b.liked_id = v.viewer_id)) "
                        + "AS connected "
                        + "FROM profile_views v JOIN users u ON u.id = v.viewer_id "
                        + "WHERE v.viewed_id = ? AND NOT EXISTS (SELECT 1 FROM blocks b "
                        + "  WHERE (b.blocker_id = v.viewed_id AND b.blocked_id = v.viewer_id) "
                        + "     OR (b.blocker_id = v.viewer_id AND b.blocked_id = v.viewed_id)) "
                        + "ORDER BY v.last_viewed_at DESC",
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    UUID pictureId = rs.getObject("profile_picture_id", UUID.class);
                    return new ActivityResponse(id, rs.getString("username"),
                            pictureId == null ? null : User.pictureUrl(id, pictureId),
                            rs.getTimestamp("last_viewed_at").toInstant(),
                            rs.getBoolean("connected"));
                },
                userId);
    }
}
