package com.glancesync.modules.fame.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import jakarta.inject.Singleton;

@Singleton
public class FameDao {

    private final Database database;

    public FameDao(Database database) {
        this.database = database;
    }

    public int recompute(UUID userId, int pointsPerViewer, int pointsPerLike, int pointsPerConnection, int pointsPerReport) {
        return database.update(
                "UPDATE users SET fame_rating = GREATEST(0, "
                        + "  ? * (SELECT COUNT(*) FROM profile_views WHERE viewed_id = users.id)"
                        + "+ ? * (SELECT COUNT(*) FROM likes WHERE liked_id = users.id AND unliked_at IS NULL)"
                        + "+ ? * (SELECT COUNT(*) FROM likes l JOIN likes back"
                        + "       ON back.liker_id = l.liked_id AND back.liked_id = l.liker_id AND back.unliked_at IS NULL"
                        + "       WHERE l.liked_id = users.id AND l.unliked_at IS NULL)"
                        + "+ ? * (SELECT COUNT(*) FROM reports WHERE reported_id = users.id)"
                        + ") WHERE id = ?",
                pointsPerViewer, pointsPerLike, pointsPerConnection, pointsPerReport, userId);
    }
}
