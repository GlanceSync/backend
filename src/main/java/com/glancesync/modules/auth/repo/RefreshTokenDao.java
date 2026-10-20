package com.glancesync.modules.auth.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class RefreshTokenDao {

    private final Database database;

    public RefreshTokenDao(Database database) {
        this.database = database;
    }

    public void create(String tokenHash, UUID userId, Instant expiresAt) {
        database.update(
                "INSERT INTO refresh_tokens (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                tokenHash, userId, Timestamp.from(expiresAt));
    }

    /**
     * Deletes the token and returns its user if it was still live. Deleting and checking
     * in one statement means a refresh token can only ever be exchanged once.
     */
    public Optional<UUID> consume(String tokenHash) {
        return database.queryOne(
                "DELETE FROM refresh_tokens WHERE token_hash = ? RETURNING user_id, expires_at > now() AS live",
                rs -> rs.getBoolean("live") ? rs.getObject("user_id", UUID.class) : null,
                tokenHash);
    }

    public void delete(String tokenHash) {
        database.update("DELETE FROM refresh_tokens WHERE token_hash = ?", tokenHash);
    }

    /** Signs the user out on every device. */
    public void deleteAllFor(UUID userId) {
        database.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
    }

    /** Housekeeping: rows for devices that never came back. */
    public void deleteExpiredFor(UUID userId) {
        database.update("DELETE FROM refresh_tokens WHERE user_id = ? AND expires_at <= now()", userId);
    }
}
