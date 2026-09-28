package com.glancesync.modules.user.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.user.entity.Picture;
import jakarta.inject.Singleton;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Singleton
public class PictureDao {

    public record PictureData(String contentType, byte[] data) {
    }

    private final Database database;

    public PictureDao(Database database) {
        this.database = database;
    }

    public List<Picture> forUser(UUID userId) {
        return database.query(
                "SELECT p.id, p.user_id, p.created_at, (u.profile_picture_id = p.id) AS is_profile "
                        + "FROM user_pictures p JOIN users u ON u.id = p.user_id "
                        + "WHERE p.user_id = ? ORDER BY p.created_at, p.id",
                rs -> {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    return new Picture(
                            rs.getObject("id", UUID.class),
                            rs.getObject("user_id", UUID.class),
                            rs.getBoolean("is_profile"),
                            createdAt == null ? null : createdAt.toInstant());
                },
                userId);
    }

    public Optional<PictureData> findData(UUID userId, UUID pictureId) {
        return database.queryOne(
                "SELECT content_type, data FROM user_pictures WHERE id = ? AND user_id = ?",
                rs -> new PictureData(rs.getString("content_type"), rs.getBytes("data")),
                pictureId, userId);
    }

    public int countForUser(UUID userId) {
        return database.queryOne(
                "SELECT COUNT(*) AS n FROM user_pictures WHERE user_id = ?",
                rs -> rs.getInt("n"),
                userId).orElse(0);
    }

    public UUID add(UUID userId, String contentType, byte[] data) {
        return database.insertReturningId(
                "INSERT INTO user_pictures (user_id, content_type, data) VALUES (?, ?, ?)",
                userId, contentType, data);
    }

    public int setProfile(UUID userId, UUID pictureId) {
        return database.update(
                "UPDATE users SET profile_picture_id = p.id FROM user_pictures p "
                        + "WHERE users.id = ? AND p.id = ? AND p.user_id = users.id",
                userId, pictureId);
    }

    public void ensureProfile(UUID userId) {
        database.update(
                "UPDATE users SET profile_picture_id = "
                        + "(SELECT id FROM user_pictures WHERE user_id = users.id ORDER BY created_at, id LIMIT 1) "
                        + "WHERE id = ? AND profile_picture_id IS NULL",
                userId);
    }

    public int delete(UUID userId, UUID pictureId) {
        return database.update(
                "DELETE FROM user_pictures WHERE id = ? AND user_id = ?",
                pictureId, userId);
    }
}
