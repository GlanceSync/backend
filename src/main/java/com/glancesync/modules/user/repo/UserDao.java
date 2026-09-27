package com.glancesync.modules.user.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.user.entity.User;
import jakarta.inject.Singleton;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Singleton
public class UserDao {

    private final Database database;

    public UserDao(Database database) {
        this.database = database;
    }

    public Optional<User> findByUsername(String username) {
        return database.queryOne(
                "SELECT id, username, password_hash, created_at, last_seen_at, avatar_updated_at "
                        + "FROM users WHERE LOWER(username) = LOWER(?)",
                UserDao::mapUser,
                username);
    }

    public Optional<User> findById(UUID id) {
        return database.queryOne(
                "SELECT id, username, password_hash, created_at, last_seen_at, avatar_updated_at "
                        + "FROM users WHERE id = ?",
                UserDao::mapUser,
                id);
    }

    public boolean usernameExists(String username) {
        return database.queryOne(
                "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)",
                rs -> true,
                username).isPresent();
    }

    public UUID create(String username, String passwordHash) {
        return database.insertReturningId(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                username, passwordHash);
    }

    /** Everyone except the caller, newest sign-ups last. */
    public List<User> findAllExcept(UUID excludedUserId) {
        return database.query(
                "SELECT id, username, password_hash, created_at, last_seen_at, avatar_updated_at "
                        + "FROM users WHERE id <> ? ORDER BY LOWER(username)",
                UserDao::mapUser,
                excludedUserId);
    }

    public void touchLastSeen(UUID userId) {
        database.update("UPDATE users SET last_seen_at = now() WHERE id = ?", userId);
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("password_hash"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("last_seen_at")),
                toInstant(rs.getTimestamp("avatar_updated_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
