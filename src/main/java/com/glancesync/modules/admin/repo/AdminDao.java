package com.glancesync.modules.admin.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.admin.dto.ReportedUserResponse;
import com.glancesync.modules.user.entity.User;

public class AdminDao {

    /** A reported account before its individual reports are attached. */
    public record ReportedUser(UUID id, String username, String firstName, String lastName, UUID profilePictureId,
                               int reportCount, Instant lastReportedAt, boolean active) {

        public String avatarUrl() {
            return profilePictureId == null ? null : User.pictureUrl(id, profilePictureId);
        }
    }

    public record ReportRow(UUID reportedId, ReportedUserResponse.Report report) {
    }

    private final Database database;

    public AdminDao(Database database) {
        this.database = database;
    }

    /** Everyone with at least one report: accounts still active first, then the most reported. */
    public List<ReportedUser> reportedUsers() {
        return database.query(
                "SELECT u.id, u.username, u.first_name, u.last_name, u.profile_picture_id, u.is_active, "
                        + "COUNT(*)::int AS report_count, MAX(r.created_at) AS last_reported_at "
                        + "FROM reports r JOIN users u ON u.id = r.reported_id "
                        + "GROUP BY u.id "
                        + "ORDER BY u.is_active DESC, report_count DESC, last_reported_at DESC",
                AdminDao::mapReportedUser);
    }

    public List<ReportRow> reports() {
        return database.query(
                "SELECT r.reported_id, r.reporter_id, u.username AS reporter_username, r.reason, r.created_at "
                        + "FROM reports r JOIN users u ON u.id = r.reporter_id "
                        + "ORDER BY r.created_at DESC",
                rs -> new ReportRow(
                        rs.getObject("reported_id", UUID.class),
                        new ReportedUserResponse.Report(
                                rs.getObject("reporter_id", UUID.class),
                                rs.getString("reporter_username"),
                                rs.getString("reason"),
                                toInstant(rs.getTimestamp("created_at")))));
    }

    /** Clears every report against an account, once an administrator has judged it genuine. */
    public int dismissReports(UUID reportedId) {
        return database.update("DELETE FROM reports WHERE reported_id = ?", reportedId);
    }

    private static ReportedUser mapReportedUser(ResultSet rs) throws SQLException {
        return new ReportedUser(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getObject("profile_picture_id", UUID.class),
                rs.getInt("report_count"),
                toInstant(rs.getTimestamp("last_reported_at")),
                rs.getBoolean("is_active"));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
