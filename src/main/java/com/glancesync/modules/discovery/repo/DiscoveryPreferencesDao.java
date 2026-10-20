package com.glancesync.modules.discovery.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.discovery.entity.DiscoveryPreferences;

import java.sql.Array;
import java.util.List;
import java.util.Optional;

public class DiscoveryPreferencesDao {

    private final Database database;

    public DiscoveryPreferencesDao(Database database) {
        this.database = database;
    }

    public Optional<DiscoveryPreferences> find(UUID userId) {
        return database.queryOne(
                "SELECT min_age, max_age, min_fame, max_fame, distance_km, city, tags, min_common_tags, sort, "
                        + "sort_order "
                        + "FROM discovery_preferences WHERE user_id = ?",
                rs -> {
                    Array tags = rs.getArray("tags");
                    return new DiscoveryPreferences(
                            rs.getObject("min_age", Integer.class),
                            rs.getObject("max_age", Integer.class),
                            rs.getObject("min_fame", Integer.class),
                            rs.getObject("max_fame", Integer.class),
                            rs.getObject("distance_km", Double.class),
                            rs.getString("city"),
                            tags == null ? List.of() : List.of((String[]) tags.getArray()),
                            rs.getObject("min_common_tags", Integer.class),
                            rs.getString("sort"),
                            rs.getString("sort_order"));
                },
                userId);
    }

    /** Replaces the user's preferences as a whole. */
    public void save(UUID userId, DiscoveryPreferences p) {
        database.update(
                "INSERT INTO discovery_preferences (user_id, min_age, max_age, min_fame, max_fame, distance_km, "
                        + "city, tags, min_common_tags, sort, sort_order, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS text[]), ?, ?, ?, now()) "
                        + "ON CONFLICT (user_id) DO UPDATE SET min_age = EXCLUDED.min_age, "
                        + "max_age = EXCLUDED.max_age, min_fame = EXCLUDED.min_fame, max_fame = EXCLUDED.max_fame, "
                        + "distance_km = EXCLUDED.distance_km, city = EXCLUDED.city, tags = EXCLUDED.tags, "
                        + "min_common_tags = EXCLUDED.min_common_tags, "
                        + "sort = EXCLUDED.sort, sort_order = EXCLUDED.sort_order, updated_at = now()",
                userId, p.minAge(), p.maxAge(), p.minFame(), p.maxFame(), p.distance(), p.city(),
                p.tags().toArray(String[]::new), p.minCommonTags(), p.sort(), p.order());
    }
}
