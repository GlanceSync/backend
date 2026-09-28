package com.glancesync.modules.fame.service;

import java.util.UUID;
import com.glancesync.common.db.Database;
import jakarta.inject.Singleton;

@Singleton
public class FameService {

    public static final int POINTS_PER_VIEWER = 1;
    public static final int POINTS_PER_LIKE = 5;
    public static final int POINTS_PER_CONNECTION = 10;
    public static final int POINTS_PER_REPORT = -20;

    private final Database database;

    public FameService(Database database) {
        this.database = database;
    }

    public void recompute(UUID userId) {
        database.update(
                "UPDATE users SET fame_rating = GREATEST(0, "
                        + "  ? * (SELECT COUNT(*) FROM profile_views WHERE viewed_id = users.id)"
                        + "+ ? * (SELECT COUNT(*) FROM likes WHERE liked_id = users.id)"
                        + "+ ? * (SELECT COUNT(*) FROM likes l JOIN likes back"
                        + "       ON back.liker_id = l.liked_id AND back.liked_id = l.liker_id"
                        + "       WHERE l.liked_id = users.id)"
                        + "+ ? * (SELECT COUNT(*) FROM reports WHERE reported_id = users.id)"
                        + ") WHERE id = ?",
                POINTS_PER_VIEWER, POINTS_PER_LIKE, POINTS_PER_CONNECTION, POINTS_PER_REPORT, userId);
    }
}
