package com.glancesync.modules.auth.repo;

import com.glancesync.common.db.Database;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class SessionDao {

    private final Database database;

    public SessionDao(Database database) {
        this.database = database;
    }

    public void create(String tokenHash, long userId, java.time.Instant expiresAt) {
        database.update(
                "INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                tokenHash, userId, java.sql.Timestamp.from(expiresAt));
    }

    /** Returns the user id for a live (unexpired) session. */
    public Optional<Long> findUserId(String tokenHash) {
        return database.queryOne(
                "SELECT user_id FROM sessions WHERE token_hash = ? AND expires_at > now()",
                rs -> rs.getLong("user_id"),
                tokenHash);
    }

    public void delete(String tokenHash) {
        database.update("DELETE FROM sessions WHERE token_hash = ?", tokenHash);
    }

    public void deleteExpired() {
        database.update("DELETE FROM sessions WHERE expires_at <= now()");
    }
}
