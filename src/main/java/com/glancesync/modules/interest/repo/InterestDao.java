package com.glancesync.modules.interest.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.interest.entity.Interest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class InterestDao {

    private static final String SELECT_INTEREST =
            "SELECT i.id, i.name, i.created_by, i.created_at FROM interests i ";

    private final Database database;

    public InterestDao(Database database) {
        this.database = database;
    }

    public List<Interest> search(String prefix, int limit) {
        return database.query(
                SELECT_INTEREST + "WHERE i.name LIKE ? ESCAPE '\\' ORDER BY i.name LIMIT ?",
                InterestDao::mapInterest,
                escapeLike(prefix) + "%", limit);
    }

    public Optional<Interest> findById(UUID id) {
        return database.queryOne(SELECT_INTEREST + "WHERE i.id = ?", InterestDao::mapInterest, id);
    }

    public Optional<Interest> findByName(String name) {
        return database.queryOne(SELECT_INTEREST + "WHERE i.name = ?", InterestDao::mapInterest, name);
    }

    public Optional<UUID> createIfAbsent(String name, UUID createdBy) {
        return database.queryOne(
                "INSERT INTO interests (name, created_by) VALUES (?, ?) "
                        + "ON CONFLICT (name) DO NOTHING RETURNING id",
                rs -> rs.getObject("id", UUID.class),
                name, createdBy);
    }

    public void rename(UUID id, String name) {
        database.update("UPDATE interests SET name = ? WHERE id = ?", name, id);
    }

    public void delete(UUID id) {
        database.update("DELETE FROM interests WHERE id = ?", id);
    }

    public int countUsersOtherThan(UUID interestId, UUID userId) {
        return database.queryOne(
                "SELECT COUNT(*) AS n FROM user_interests WHERE interest_id = ? AND user_id <> ?",
                rs -> rs.getInt("n"),
                interestId, userId).orElse(0);
    }

    // --- A user's own list -------------------------------------------------------------

    public List<Interest> forUser(UUID userId) {
        return database.query(
                SELECT_INTEREST + "JOIN user_interests mine ON mine.interest_id = i.id "
                        + "WHERE mine.user_id = ? ORDER BY i.name",
                InterestDao::mapInterest,
                userId);
    }

    public int countForUser(UUID userId) {
        return database.queryOne(
                "SELECT COUNT(*) AS n FROM user_interests WHERE user_id = ?",
                rs -> rs.getInt("n"),
                userId).orElse(0);
    }

    public void addToUser(UUID userId, UUID interestId) {
        database.update(
                "INSERT INTO user_interests (user_id, interest_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId, interestId);
    }

    public int removeFromUser(UUID userId, UUID interestId) {
        return database.update(
                "DELETE FROM user_interests WHERE user_id = ? AND interest_id = ?",
                userId, interestId);
    }

    public void replaceForUser(UUID userId, List<UUID> interestIds) {
        String[] ids = interestIds.stream().map(UUID::toString).toArray(String[]::new);
        database.update(
                "DELETE FROM user_interests WHERE user_id = ? "
                        + "AND NOT (interest_id = ANY (CAST(? AS uuid[])))",
                userId, ids);
        database.update(
                "INSERT INTO user_interests (user_id, interest_id) "
                        + "SELECT ?, unnest(CAST(? AS uuid[])) ON CONFLICT DO NOTHING",
                userId, ids);
    }

    private static Interest mapInterest(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new Interest(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("created_by", UUID.class),
                createdAt == null ? null : createdAt.toInstant());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
