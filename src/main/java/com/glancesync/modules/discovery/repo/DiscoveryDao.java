package com.glancesync.modules.discovery.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import com.glancesync.modules.discovery.dto.DiscoveryFilters;
import com.glancesync.modules.user.enums.Gender;
import com.glancesync.modules.user.enums.SexualPreference;
import com.glancesync.modules.user.entity.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiscoveryDao {

    /** Within this distance, or in the same city, counts as "your area". */
    public static final int SAME_AREA_KM = 50;

    // Match score for the default ranking: points per shared tag, plus a little per fame
    // point, minus a little per km of distance.
    static final double SCORE_PER_COMMON_TAG = 10;
    static final double SCORE_PER_FAME = 0.1;
    static final double SCORE_LOST_PER_KM = 0.1;
    /** Distance stops costing more past this, and people without one are treated as this far. */
    static final double SCORE_DISTANCE_CAP_KM = 200;

    /** {@code pictureIds} starts with the profile picture, then the rest oldest first. */
    public record Row(UUID id, String username, String firstName, String biography, UUID profilePictureId,
                      Gender gender, Integer age, String city, Double distanceMeters, int fameRating, int commonTags,
                      List<String> interests, List<UUID> pictureIds) {

        public String avatarUrl() {
            return profilePictureId == null ? null : User.pictureUrl(id, profilePictureId);
        }

        public List<String> pictureUrls() {
            return pictureIds.stream().map(pictureId -> User.pictureUrl(id, pictureId)).toList();
        }
    }

    private final Database database;

    public DiscoveryDao(Database database) {
        this.database = database;
    }

    /**
     * Only people whose gender and orientation fit the viewer's both ways, leaving out
     * anyone the viewer already liked when {@code filters.excludeLiked()}.
     */
    public List<Row> find(User viewer, DiscoveryFilters filters) {
        List<Object> params = new ArrayList<>();

        // The viewer's own location, read once. NULL when they have no coordinates, which
        // makes every distance NULL and every ST_DWithin false.
        StringBuilder sql = new StringBuilder("WITH me AS (SELECT location FROM users WHERE id = ?) ");
        params.add(viewer.id());

        // --- Computed columns ---------------------------------------------------------
        // Tags and pictures sit in the outer select, so they are only gathered for the page.
        sql.append("SELECT c.*, "
                + "ARRAY(SELECT i.name FROM user_interests ui JOIN interests i ON i.id = ui.interest_id "
                + "WHERE ui.user_id = c.id ORDER BY i.name) AS interests, "
                + "ARRAY(SELECT p.id::text FROM user_pictures p WHERE p.user_id = c.id "
                + "ORDER BY (p.id = c.profile_picture_id) DESC, p.created_at, p.id) AS picture_ids "
                + "FROM (SELECT u.id, u.username, u.first_name, u.biography, u.profile_picture_id, "
                + "u.gender, u.city, u.fame_rating, "
                + "EXTRACT(YEAR FROM age(current_date, u.birth_date))::int AS age, "
                + "ST_Distance(u.location, me.location) AS distance_m, ");

        sql.append("common.common_tags, ");
        sql.append("? * common.common_tags + ? * u.fame_rating "
                + "- ? * COALESCE(LEAST(ST_Distance(u.location, me.location) / 1000, ?), ?) AS score, ");
        params.add(SCORE_PER_COMMON_TAG);
        params.add(SCORE_PER_FAME);
        params.add(SCORE_LOST_PER_KM);
        params.add(SCORE_DISTANCE_CAP_KM);
        params.add(SCORE_DISTANCE_CAP_KM);

        // COALESCE: a NULL here would sort before true in DESC order.
        sql.append("COALESCE(ST_DWithin(u.location, me.location, ?) "
                + "OR (u.city IS NOT NULL AND LOWER(u.city) = LOWER(CAST(? AS text))), false) AS same_area ");
        params.add(SAME_AREA_KM * 1000);
        params.add(viewer.city());

        // --- Who can appear at all ------------------------------------------------------
        sql.append("FROM users u CROSS JOIN me CROSS JOIN LATERAL ("
                + "SELECT COUNT(*)::int AS common_tags FROM user_interests theirs JOIN user_interests mine "
                + "ON mine.interest_id = theirs.interest_id "
                + "WHERE theirs.user_id = u.id AND mine.user_id = ?) common ");
        params.add(viewer.id());
        sql.append("WHERE u.id <> ? AND u.profile_completed ");
        params.add(viewer.id());

        sql.append("AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = ? AND b.blocked_id = u.id) "
                + "OR (b.blocker_id = u.id AND b.blocked_id = ?)) ");
        params.add(viewer.id());
        params.add(viewer.id());

        if (filters.excludeLiked()) {
            sql.append("AND NOT EXISTS (SELECT 1 FROM likes l WHERE l.liker_id = ? AND l.liked_id = u.id AND l.unliked_at IS NULL) ");
            params.add(viewer.id());
        }
        appendOrientation(sql, params, viewer);

        // --- Filters on stored columns -------------------------------------------------
        if (filters.minAge() != null) {
            sql.append("AND u.birth_date <= current_date - make_interval(years => ?) ");
            params.add(filters.minAge());
        }
        if (filters.maxAge() != null) {
            // Still maxAge on the day before their next birthday.
            sql.append("AND u.birth_date > current_date - make_interval(years => ?) ");
            params.add(filters.maxAge() + 1);
        }
        if (filters.minFame() != null) {
            sql.append("AND u.fame_rating >= ? ");
            params.add(filters.minFame());
        }
        if (filters.maxFame() != null) {
            sql.append("AND u.fame_rating <= ? ");
            params.add(filters.maxFame());
        }
        if (filters.city() != null) {
            sql.append("AND LOWER(u.city) = LOWER(?) ");
            params.add(filters.city());
        }
        if (filters.distanceKm() != null) {
            // ST_DWithin rather than comparing ST_Distance, so the GiST index does the work.
            sql.append("AND ST_DWithin(u.location, me.location, ?) ");
            params.add(filters.distanceKm() * 1000);
        }
        if (!filters.tags().isEmpty()) {
            // Everyone who has every one of the requested tags.
            sql.append("AND u.id IN (SELECT ui.user_id FROM user_interests ui "
                    + "JOIN interests i ON i.id = ui.interest_id "
                    + "WHERE i.name = ANY (CAST(? AS text[])) GROUP BY ui.user_id HAVING COUNT(*) = ?) ");
            params.add(filters.tags().toArray(String[]::new));
            params.add(filters.tags().size());
        }
        if (filters.minCommonTags() != null) {
            sql.append("AND common.common_tags >= ? ");
            params.add(filters.minCommonTags());
        }
        sql.append(") c ");

        // --- Order and page ---------------------------------------------------------------
        if (filters.sort() != null) {
            sql.append("ORDER BY ").append(filters.sort().column())
                    .append(filters.ascending() ? " ASC" : " DESC").append(" NULLS LAST, ");
        } else {
            sql.append("ORDER BY c.same_area DESC, ");
        }
        // Default ranking, and tie-breakers after an explicit sort.
        sql.append("c.score DESC, c.distance_m ASC NULLS LAST, c.id ");
        sql.append("LIMIT ? OFFSET ?");
        params.add(filters.limit());
        params.add(filters.offset());

        return database.query(sql.toString(), rs -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("biography"),
                        rs.getObject("profile_picture_id", UUID.class),
                        Gender.fromDb(rs.getString("gender")),
                        rs.getObject("age", Integer.class),
                        rs.getString("city"),
                        rs.getObject("distance_m", Double.class),
                        rs.getInt("fame_rating"),
                        rs.getInt("common_tags"),
                        List.of((String[]) rs.getArray("interests").getArray()),
                        Arrays.stream((String[]) rs.getArray("picture_ids").getArray()).map(UUID::fromString).toList()),
                params.toArray());
    }

    /**
     * Both sides must want each other. The viewer wants the genders their preference points
     * at; the candidate's own preference must point back at the viewer's gender.
     */
    private static void appendOrientation(StringBuilder sql, List<Object> params, User viewer) {
        Gender gender = viewer.gender();
        if (gender == null) {
            return;
        }
        Gender other = gender == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        List<String> wanted = switch (viewer.sexualPreference() == null
                ? SexualPreference.DEFAULT : viewer.sexualPreference()) {
            case HETEROSEXUAL -> List.of(other.dbValue());
            case HOMOSEXUAL -> List.of(gender.dbValue());
            case BISEXUAL -> List.of(Gender.MALE.dbValue(), Gender.FEMALE.dbValue());
        };

        sql.append("AND u.gender = ANY (CAST(? AS text[])) ");
        params.add(wanted.toArray(String[]::new));

        sql.append("AND (u.sexual_preference = 'bisexual' "
                + "OR (u.sexual_preference = 'heterosexual' AND u.gender <> ?) "
                + "OR (u.sexual_preference = 'homosexual' AND u.gender = ?)) ");
        params.add(gender.dbValue());
        params.add(gender.dbValue());
    }
}
