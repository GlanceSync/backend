package com.glancesync.modules.report.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;

public class ReportDao {

    private final Database database;

    public ReportDao(Database database) {
        this.database = database;
    }

    public int report(UUID reporterId, UUID reportedId, String reason) {
        return database.update(
                "INSERT INTO reports (reporter_id, reported_id, reason) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                reporterId, reportedId, reason);
    }
}
