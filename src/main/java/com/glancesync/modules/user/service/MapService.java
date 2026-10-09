package com.glancesync.modules.user.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.realtime.service.PresenceRegistry;
import com.glancesync.modules.user.dto.MapUserResponse;
import com.glancesync.modules.user.entity.MapUser;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.HttpStatus;

/** Who to draw on the map for the part of it someone is looking at. */
public class MapService {

    /** A map zoomed out over a whole country would otherwise send everyone in it. */
    public static final int MAX_MARKERS = 300;
    /** About 330 m of latitude: close enough to be useful, too coarse to find a front door. */
    private static final double GRID_DEGREES = 0.003;

    private final UserDao userDao;
    private final PresenceRegistry presence;

    public MapService(UserDao userDao, PresenceRegistry presence) {
        this.userDao = userDao;
        this.presence = presence;
    }

    public List<MapUserResponse> inBounds(UUID selfId, Double minLatitude, Double minLongitude,
                                          Double maxLatitude, Double maxLongitude) {
        if (minLatitude == null || minLongitude == null || maxLatitude == null || maxLongitude == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Send the four edges of the visible map.");
        }
        if (!inRange(minLatitude, -90, 90) || !inRange(maxLatitude, -90, 90)
                || !inRange(minLongitude, -180, 180) || !inRange(maxLongitude, -180, 180)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Those map edges are not on Earth.");
        }
        if (minLatitude > maxLatitude || minLongitude > maxLongitude) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The minimum edges must come before the maximum ones.",
                    Map.of("bounds", "Invalid."));
        }

        return userDao.findOnMap(selfId, minLatitude, minLongitude, maxLatitude, maxLongitude,
                        GRID_DEGREES, MAX_MARKERS).stream()
                .map(this::toResponse)
                .toList();
    }

    private MapUserResponse toResponse(MapUser user) {
        Double distanceKm = user.distanceMeters() == null ? null : (double) Math.round(user.distanceMeters() / 1000);
        return new MapUserResponse(user.id(), user.username(), user.firstName(), user.avatarUrl(), user.age(),
                presence.isOnline(user.id()), user.lastSeenAt(), user.latitude(), user.longitude(),
                distanceKm, user.commonInterests());
    }

    private static boolean inRange(double value, double min, double max) {
        return value >= min && value <= max;
    }
}
