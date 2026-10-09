package com.glancesync.modules.user.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.user.enums.Gender;
import com.glancesync.modules.user.enums.LocationSource;
import com.glancesync.modules.user.enums.SexualPreference;
import com.glancesync.modules.user.entity.MapUser;
import com.glancesync.modules.user.entity.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class UserDao {

    private static final String SELECT_USER =
            "SELECT id, username, email, first_name, last_name, password_hash, email_verified_at, "
                    + "created_at, last_seen_at, profile_picture_id, gender, sexual_preference, biography, birth_date, "
                    + "ST_Y(location::geometry) AS latitude, ST_X(location::geometry) AS longitude, city, location_source, location_updated_at, "
                    + "location_accuracy_m, show_on_map, fame_rating, profile_completed FROM users ";

    private final Database database;

    public UserDao(Database database) {
        this.database = database;
    }

    public Optional<User> findByUsername(String username) {
        return database.queryOne(
                SELECT_USER + "WHERE LOWER(username) = LOWER(?)",
                UserDao::mapUser,
                username);
    }

    public Optional<User> findById(UUID id) {
        return database.queryOne(
                SELECT_USER + "WHERE id = ?",
                UserDao::mapUser,
                id);
    }

    public boolean usernameExists(String username) {
        return database.queryOne(
                "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)",
                rs -> true,
                username).isPresent();
    }

    public Optional<User> findByEmail(String email) {
        return database.queryOne(SELECT_USER + "WHERE LOWER(email) = LOWER(?)", UserDao::mapUser, email);
    }

    public boolean emailExists(String email) {
        return database.queryOne(
                "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)",
                rs -> true,
                email).isPresent();
    }

    public UUID create(String username, String email, String firstName, String lastName, String passwordHash) {
        return database.insertReturningId(
                "INSERT INTO users (username, email, first_name, last_name, password_hash) "
                        + "VALUES (?, ?, ?, ?, ?)",
                username, email, firstName, lastName, passwordHash);
    }

    public List<User> findAllExcept(UUID excludedUserId) {
        return database.query(
                SELECT_USER + "WHERE id <> ? ORDER BY LOWER(username)",
                UserDao::mapUser,
                excludedUserId);
    }

    public void updateProfile(UUID userId, Gender gender, SexualPreference sexualPreference, String biography,
                              LocalDate birthDate) {
        database.update(
                "UPDATE users SET gender = ?, sexual_preference = ?, biography = ?, birth_date = ? WHERE id = ?",
                gender == null ? null : gender.dbValue(), sexualPreference.dbValue(), biography, birthDate, userId);
    }

    public void updateLocation(UUID userId, Double latitude, Double longitude, Double accuracyMeters, String city,
                               LocationSource source) {
        if (latitude == null || longitude == null) {
            database.update(
                    "UPDATE users SET location = NULL, location_accuracy_m = NULL, city = ?, location_source = ?, "
                            + "location_updated_at = now() WHERE id = ?",
                    city, source.dbValue(), userId);
            return;
        }
        database.update(
                "UPDATE users SET location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, "
                        + "location_accuracy_m = ?, city = ?, location_source = ?, location_updated_at = now() "
                        + "WHERE id = ?",
                longitude, latitude, accuracyMeters, city, source.dbValue(), userId);
    }

    public void updateShowOnMap(UUID userId, boolean showOnMap) {
        database.update("UPDATE users SET show_on_map = ? WHERE id = ?", showOnMap, userId);
    }

    /**
     * People inside a longitude/latitude box, nearest to {@code selfId} first. Positions are
     * snapped to a grid of {@code gridDegrees} so the map never reveals where someone lives;
     * the distance is exact and left for the caller to round.
     */
    public List<MapUser> findOnMap(UUID selfId, double minLatitude, double minLongitude, double maxLatitude,
                                   double maxLongitude, double gridDegrees, int limit) {
        return database.query(
                "SELECT u.id, u.username, u.first_name, u.profile_picture_id, u.birth_date, u.last_seen_at, "
                        + "ST_Y(ST_SnapToGrid(u.location::geometry, ?)) AS latitude, "
                        + "ST_X(ST_SnapToGrid(u.location::geometry, ?)) AS longitude, "
                        + "ST_Distance(u.location, me.location) AS meters, "
                        + "ARRAY(SELECT i.name FROM user_interests mine "
                        + "JOIN user_interests theirs ON theirs.interest_id = mine.interest_id "
                        + "JOIN interests i ON i.id = mine.interest_id "
                        + "WHERE mine.user_id = me.id AND theirs.user_id = u.id ORDER BY i.name) AS common_interests "
                        + "FROM users me JOIN users u ON u.id <> me.id "
                        + "WHERE me.id = ? AND u.profile_completed AND u.show_on_map AND u.location IS NOT NULL "
                        + "AND u.location::geometry && ST_MakeEnvelope(?, ?, ?, ?, 4326) "
                        + "AND NOT EXISTS (SELECT 1 FROM blocks b "
                        + "WHERE (b.blocker_id = me.id AND b.blocked_id = u.id) "
                        + "OR (b.blocker_id = u.id AND b.blocked_id = me.id)) "
                        + "ORDER BY meters NULLS LAST, u.fame_rating DESC LIMIT ?",
                UserDao::mapMapUser,
                gridDegrees, gridDegrees, selfId, minLongitude, minLatitude, maxLongitude, maxLatitude, limit);
    }

    public Optional<Double> distanceMeters(UUID userA, UUID userB) {
        return database.queryOne(
                "SELECT ST_Distance(a.location, b.location) AS meters FROM users a, users b "
                        + "WHERE a.id = ? AND b.id = ? AND a.location IS NOT NULL AND b.location IS NOT NULL",
                rs -> rs.getObject("meters", Double.class),
                userA, userB);
    }

    public void refreshProfileCompleted(UUID userId) {
        database.update(
                "UPDATE users SET profile_completed = ("
                        + "gender IS NOT NULL AND birth_date IS NOT NULL AND biography IS NOT NULL "
                        + "AND profile_picture_id IS NOT NULL AND location_source IS NOT NULL "
                        + "AND EXISTS (SELECT 1 FROM user_interests WHERE user_id = users.id)"
                        + ") WHERE id = ?",
                userId);
    }

    public void updateNames(UUID userId, String firstName, String lastName) {
        database.update("UPDATE users SET first_name = ?, last_name = ? WHERE id = ?", firstName, lastName, userId);
    }

    public void updateUsername(UUID userId, String username) {
        database.update("UPDATE users SET username = ? WHERE id = ?", username, userId);
    }

    public void updatePasswordHash(UUID userId, String passwordHash) {
        database.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, userId);
    }

    public void markEmailVerified(UUID userId) {
        database.update(
                "UPDATE users SET email_verified_at = COALESCE(email_verified_at, now()) WHERE id = ?", userId);
    }

    public void touchLastSeen(UUID userId) {
        database.update("UPDATE users SET last_seen_at = now() WHERE id = ?", userId);
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("password_hash"),
                toInstant(rs.getTimestamp("email_verified_at")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("last_seen_at")),
                rs.getObject("profile_picture_id", UUID.class),
                Gender.fromDb(rs.getString("gender")),
                SexualPreference.fromDb(rs.getString("sexual_preference")),
                rs.getString("biography"),
                rs.getObject("birth_date", LocalDate.class),
                rs.getObject("latitude", Double.class),
                rs.getObject("longitude", Double.class),
                rs.getString("city"),
                LocationSource.fromDb(rs.getString("location_source")),
                toInstant(rs.getTimestamp("location_updated_at")),
                getNullableDouble(rs, "location_accuracy_m"),
                rs.getBoolean("show_on_map"),
                rs.getInt("fame_rating"),
                rs.getBoolean("profile_completed"));
    }

    private static MapUser mapMapUser(ResultSet rs) throws SQLException {
        return new MapUser(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getObject("profile_picture_id", UUID.class),
                rs.getObject("birth_date", LocalDate.class),
                toInstant(rs.getTimestamp("last_seen_at")),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getObject("meters", Double.class),
                List.of((String[]) rs.getArray("common_interests").getArray()));
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
