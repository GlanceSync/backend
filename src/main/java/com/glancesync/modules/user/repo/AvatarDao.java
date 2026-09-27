package com.glancesync.modules.user.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class AvatarDao {

    public record Avatar(String contentType, byte[] data) {
    }

    private final Database database;

    public AvatarDao(Database database) {
        this.database = database;
    }

    public Optional<Avatar> find(UUID userId) {
        return database.queryOne(
                "SELECT content_type, data FROM user_avatars WHERE user_id = ?",
                rs -> new Avatar(rs.getString("content_type"), rs.getBytes("data")),
                userId);
    }

    /**
     * Stores the photo and bumps the user's avatar version in one statement, so the
     * URL handed to clients never points at an image that is not there yet.
     */
    public void save(UUID userId, String contentType, byte[] data) {
        database.update(
                "WITH saved AS ("
                        + "  INSERT INTO user_avatars (user_id, content_type, data, updated_at)"
                        + "  VALUES (?, ?, ?, now())"
                        + "  ON CONFLICT (user_id) DO UPDATE"
                        + "  SET content_type = EXCLUDED.content_type, data = EXCLUDED.data,"
                        + "      updated_at = EXCLUDED.updated_at"
                        + "  RETURNING user_id, updated_at"
                        + ") "
                        + "UPDATE users SET avatar_updated_at = saved.updated_at "
                        + "FROM saved WHERE users.id = saved.user_id",
                userId, contentType, data);
    }

    public void delete(UUID userId) {
        database.update(
                "WITH removed AS (DELETE FROM user_avatars WHERE user_id = ? RETURNING user_id) "
                        + "UPDATE users SET avatar_updated_at = NULL "
                        + "FROM removed WHERE users.id = removed.user_id",
                userId);
    }
}
