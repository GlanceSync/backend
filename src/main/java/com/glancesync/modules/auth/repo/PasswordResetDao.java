package com.glancesync.modules.auth.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import jakarta.inject.Singleton;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class PasswordResetDao {

    private final Database database;

    public PasswordResetDao(Database database) {
        this.database = database;
    }

    public void create(String tokenHash, UUID userId, Instant expiresAt) {
        database.update(
                "INSERT INTO password_resets (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                tokenHash, userId, Timestamp.from(expiresAt));
    }

    /** Looks the token up without using it, so a rejected new password does not burn the link. */
    public Optional<UUID> findLive(String tokenHash) {
        return database.queryOne(
                "SELECT user_id FROM password_resets WHERE token_hash = ? AND expires_at > now()",
                rs -> rs.getObject("user_id", UUID.class),
                tokenHash);
    }

    /** Deletes the token and returns its user if it was still live, so a link works once. */
    public Optional<UUID> consume(String tokenHash) {
        return database.queryOne(
                "DELETE FROM password_resets WHERE token_hash = ? RETURNING user_id, expires_at > now() AS live",
                rs -> rs.getBoolean("live") ? rs.getObject("user_id", UUID.class) : null,
                tokenHash);
    }

    public boolean sentWithin(UUID userId, int seconds) {
        return database.queryOne(
                "SELECT 1 FROM password_resets "
                        + "WHERE user_id = ? AND created_at > now() - make_interval(secs => ?)",
                rs -> true,
                userId, seconds).isPresent();
    }

    public void deleteForUser(UUID userId) {
        database.update("DELETE FROM password_resets WHERE user_id = ?", userId);
    }
}
