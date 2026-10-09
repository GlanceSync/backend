package com.glancesync.modules.discovery.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.common.geo.ApproximateDistance;
import com.glancesync.modules.discovery.dto.DiscoveryCard;
import com.glancesync.modules.discovery.dto.DiscoveryFilters;
import com.glancesync.modules.discovery.dto.DiscoverySort;
import com.glancesync.modules.discovery.entity.DiscoveryPreferences;
import com.glancesync.modules.discovery.repo.DiscoveryDao;
import com.glancesync.modules.discovery.repo.DiscoveryPreferencesDao;
import com.glancesync.modules.realtime.service.PresenceRegistry;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.HttpStatus;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Discover: the people a user is shown, filtered by the preferences saved on their account.
 * It leaves out the viewer, anyone blocked either way, anyone whose profile is not complete
 * and anyone the viewer already liked, and matches orientation both ways.
 *
 * Without a sort, results are ranked: people in the viewer's area first (within
 * {@value DiscoveryDao#SAME_AREA_KM} km or the same city), then by a match score that weighs
 * shared tags, fame and distance together.
 */
@Singleton
public class DiscoveryService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    private static final int MIN_AGE = 18;
    private static final int MAX_AGE = 120;
    private static final int MAX_TAGS = 10;
    private static final int MAX_CITY_LENGTH = 100;
    private static final double MAX_DISTANCE_KM = 20_000;

    private final DiscoveryDao discoveryDao;
    private final DiscoveryPreferencesDao preferencesDao;
    private final UserDao userDao;
    private final PresenceRegistry presence;

    public DiscoveryService(DiscoveryDao discoveryDao, DiscoveryPreferencesDao preferencesDao, UserDao userDao,
                            PresenceRegistry presence) {
        this.discoveryDao = discoveryDao;
        this.preferencesDao = preferencesDao;
        this.userDao = userDao;
        this.presence = presence;
    }

    public DiscoveryPreferences preferences(UUID selfId) {
        return preferencesDao.find(selfId).orElse(DiscoveryPreferences.NONE);
    }

    /** Replaces the saved preferences as a whole; a field left out is cleared. */
    public DiscoveryPreferences savePreferences(UUID selfId, DiscoveryPreferences request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        DiscoveryPreferences normalised = normalise(request);
        if (normalised.distance() != null && !hasCoordinates(requireUser(selfId))) {
            throw invalid("distance", "Share your location with coordinates to filter by distance, or use a city.");
        }
        preferencesDao.save(selfId, normalised);
        return normalised;
    }

    /**
     * Uses the saved preferences, with any field in {@code overrides} replacing the saved one
     * for this request only. Nothing is saved here.
     */
    public List<DiscoveryCard> discover(UUID selfId, DiscoveryPreferences overrides, Integer limit, Integer offset) {
        User viewer = requireUser(selfId);
        if (!viewer.profileCompleted()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Complete your profile to discover people.");
        }
        DiscoveryPreferences saved = preferences(selfId);
        DiscoveryPreferences criteria = normalise(saved.overriddenBy(normalise(overrides)));

        if (criteria.distance() != null && !hasCoordinates(viewer)) {
            if (overrides.distance() != null) {
                throw invalid("distance",
                        "Share your location with coordinates to filter by distance, or use a city.");
            }
            // A saved distance from before the user switched to a city-only location.
            criteria = criteria.withoutDistance();
        }

        DiscoveryFilters filters = toFilters(criteria, page(limit, offset));
        return discoveryDao.find(viewer, filters).stream()
                .map(row -> new DiscoveryCard(row.id(), row.username(), row.avatarUrl(), row.gender(), row.age(),
                        row.city(), ApproximateDistance.km(row.distanceMeters()),
                        ApproximateDistance.label(row.distanceMeters()), row.fameRating(), row.commonTags(),
                        presence.isOnline(row.id())))
                .toList();
    }

    /** Validates and normalises the preferences, throwing an exception for any invalid field. */
    private static DiscoveryPreferences normalise(DiscoveryPreferences p) {
        Integer minAge = p.minAge();
        Integer maxAge = p.maxAge();
        if (minAge != null && (minAge < MIN_AGE || minAge > MAX_AGE)) {
            throw invalid("minAge", "Ages go from " + MIN_AGE + " to " + MAX_AGE + ".");
        }
        if (maxAge != null && (maxAge < MIN_AGE || maxAge > MAX_AGE)) {
            throw invalid("maxAge", "Ages go from " + MIN_AGE + " to " + MAX_AGE + ".");
        }
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw invalid("maxAge", "The maximum age must not be below the minimum.");
        }

        if (p.minFame() != null && p.minFame() < 0) {
            throw invalid("minFame", "Fame ratings start at 0.");
        }
        if (p.maxFame() != null && p.maxFame() < 0) {
            throw invalid("maxFame", "Fame ratings start at 0.");
        }
        if (p.minFame() != null && p.maxFame() != null && p.minFame() > p.maxFame()) {
            throw invalid("maxFame", "The maximum fame must not be below the minimum.");
        }

        if (p.distance() != null && (p.distance() <= 0 || p.distance() > MAX_DISTANCE_KM)) {
            throw invalid("distance", "Pick a distance between 0 and " + (int) MAX_DISTANCE_KM + " km.");
        }

        String city = p.city() == null || p.city().isBlank() ? null : p.city().strip();
        if (city != null && city.length() > MAX_CITY_LENGTH) {
            throw invalid("city", "The city can be at most " + MAX_CITY_LENGTH + " characters.");
        }

        List<String> tags = p.tags() == null ? List.of() : p.tags().stream()
                .filter(tag -> tag != null)
                .map(tag -> tag.trim().replaceFirst("^#+", "").toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
        if (tags.size() > MAX_TAGS) {
            throw invalid("tags", "Filter by at most " + MAX_TAGS + " tags.");
        }

        if (p.minCommonTags() != null && p.minCommonTags() < 0) {
            throw invalid("minCommonTags", "The number of common tags cannot be negative.");
        }

        DiscoverySort sort = parseSort(p.sort());
        String order = p.order() == null || p.order().isBlank() ? null : p.order().trim().toLowerCase(Locale.ROOT);
        if (order != null && !order.equals("asc") && !order.equals("desc")) {
            throw invalid("order", "Order is asc or desc.");
        }

        return new DiscoveryPreferences(minAge, maxAge, p.minFame(), p.maxFame(), p.distance(), city, tags,
                p.minCommonTags(), sort == null ? null : sort.paramName(), order);
    }

    private record Page(int limit, int offset) {
    }

    private static Page page(Integer limit, Integer offset) {
        int l = limit == null ? DEFAULT_LIMIT : limit;
        if (l < 1 || l > MAX_LIMIT) {
            throw invalid("limit", "The limit goes from 1 to " + MAX_LIMIT + ".");
        }
        int o = offset == null ? 0 : offset;
        if (o < 0) {
            throw invalid("offset", "The offset cannot be negative.");
        }
        return new Page(l, o);
    }

    private static DiscoveryFilters toFilters(DiscoveryPreferences p, Page page) {
        DiscoverySort sort = parseSort(p.sort());
        boolean ascending = p.order() == null
                ? sort == null || sort.ascendingByDefault()
                : p.order().equals("asc");
        return new DiscoveryFilters(p.minAge(), p.maxAge(), p.minFame(), p.maxFame(), p.distance(), p.city(),
                p.tags(), p.minCommonTags(), sort, ascending, page.limit(), page.offset());
    }

    /** age | distance | fame | commonTags, case-insensitive. */
    private static DiscoverySort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "age" -> DiscoverySort.AGE;
            case "distance" -> DiscoverySort.DISTANCE;
            case "fame" -> DiscoverySort.FAME;
            case "commontags", "common_tags", "tags" -> DiscoverySort.COMMON_TAGS;
            default -> throw invalid("sort", "Sort by age, distance, fame or commonTags.");
        };
    }

    private static boolean hasCoordinates(User user) {
        return user.latitude() != null && user.longitude() != null;
    }

    private User requireUser(UUID userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, message));
    }
}
