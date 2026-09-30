package com.glancesync.modules.like.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.user.dto.ActivityResponse;
import com.glancesync.modules.user.entity.User;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

@Singleton
public class LikeDao {

    private final Database database;

    public LikeDao(Database database) {
        this.database = database;
    }

    public int like(UUID likerId, UUID likedId) {
        return database.update(
                "INSERT INTO likes (liker_id, liked_id) VALUES (?, ?) "
                        // Liking again revives an unliked row; an existing live like is left alone.
                        + "ON CONFLICT (liker_id, liked_id) DO UPDATE SET unliked_at = NULL, created_at = now() "
                        + "WHERE likes.unliked_at IS NOT NULL",
                likerId, likedId);
    }

    public int unlike(UUID likerId, UUID likedId) {
        return database.update(
                "UPDATE likes SET unliked_at = now() "
                        + "WHERE liker_id = ? AND liked_id = ? AND unliked_at IS NULL",
                likerId, likedId);
    }

    public boolean likes(UUID likerId, UUID likedId) {
        return database.queryOne(
                "SELECT 1 FROM likes WHERE liker_id = ? AND liked_id = ? AND unliked_at IS NULL",
                rs -> true,
                likerId, likedId).isPresent();
    }

    public boolean hasUnliked(UUID unlikerId, UUID unlikedId) {
        return database.queryOne(
                "SELECT 1 FROM likes WHERE liker_id = ? AND liked_id = ? AND unliked_at IS NOT NULL",
                rs -> true,
                unlikerId, unlikedId).isPresent();
    }

    /** Everyone this user is connected with: both of them liked each other. */
    public Set<UUID> connectionsOf(UUID userId) {
        List<UUID> ids = database.query(
                "SELECT l.liked_id FROM likes l "
                        + "JOIN likes back ON back.liker_id = l.liked_id AND back.liked_id = l.liker_id "
                        + "  AND back.unliked_at IS NULL "
                        + "WHERE l.liker_id = ? AND l.unliked_at IS NULL",
                rs -> rs.getObject("liked_id", UUID.class),
                userId);
        return Set.copyOf(ids);
    }

    public List<ActivityResponse> likedBy(UUID userId) {
        return database.query(
                "SELECT u.id, u.username, u.profile_picture_id, l.created_at, "
                        + "EXISTS (SELECT 1 FROM likes back WHERE back.liker_id = l.liked_id "
                        + "        AND back.liked_id = l.liker_id AND back.unliked_at IS NULL) AS connected "
                        + "FROM likes l JOIN users u ON u.id = l.liker_id "
                        + "WHERE l.liked_id = ? AND l.unliked_at IS NULL AND NOT EXISTS (SELECT 1 FROM blocks b "
                        + "  WHERE (b.blocker_id = l.liked_id AND b.blocked_id = l.liker_id) "
                        + "     OR (b.blocker_id = l.liker_id AND b.blocked_id = l.liked_id)) "
                        + "ORDER BY l.created_at DESC",
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    UUID pictureId = rs.getObject("profile_picture_id", UUID.class);
                    return new ActivityResponse(id, rs.getString("username"),
                            pictureId == null ? null : User.pictureUrl(id, pictureId),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getBoolean("connected"));
                },
                userId);
    }
}
