package com.glancesync.modules.user.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.common.geo.ApproximateDistance;
import com.glancesync.modules.interest.entity.Interest;
import com.glancesync.modules.interest.repo.InterestDao;
import com.glancesync.modules.like.dto.LikeStatus;
import com.glancesync.modules.like.service.LikeService;
import com.glancesync.modules.realtime.service.PresenceRegistry;
import com.glancesync.modules.user.dto.LocationRequest;
import com.glancesync.modules.user.dto.MapVisibilityRequest;
import com.glancesync.modules.user.dto.PictureResponse;
import com.glancesync.modules.user.dto.ProfileRequest;
import com.glancesync.modules.user.dto.ProfileResponse;
import com.glancesync.modules.user.enums.LocationSource;
import com.glancesync.modules.user.enums.SexualPreference;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.PictureDao;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.modules.visit.service.ProfileViewService;
import com.glancesync.web.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class ProfileService {

    public static final int MAX_BIOGRAPHY_LENGTH = 1000;
    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 120;
    public static final int MAX_CITY_LENGTH = 100;
    /** Anything vaguer than this is an IP guess, not a GPS fix worth storing as one. */
    public static final double MAX_GPS_ACCURACY_METERS = 100_000;


    private final UserDao userDao;
    private final PictureDao pictureDao;
    private final InterestDao interestDao;
    private final LikeService likeService;
    private final ProfileViewService viewService;
    private final PresenceRegistry presence;

    public ProfileService(UserDao userDao, PictureDao pictureDao, InterestDao interestDao, LikeService likeService,
                          ProfileViewService viewService, PresenceRegistry presence) {
        this.userDao = userDao;
        this.pictureDao = pictureDao;
        this.interestDao = interestDao;
        this.likeService = likeService;
        this.viewService = viewService;
        this.presence = presence;
    }

    public User get(UUID userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists."));
    }

    public ProfileResponse mine(UUID selfId) {
        User self = get(selfId);
        return new ProfileResponse(self.id(), self.username(), self.firstName(), self.lastName(),
                self.avatarUrl(), self.gender(), self.sexualPreference(), self.biography(),
                self.age(), self.city(), self.fameRating(), presence.isOnline(selfId), self.lastSeenAt(),
                interestNames(selfId), pictures(selfId),
                self.birthDate(), self.latitude(), self.longitude(), self.locationAccuracyM(), self.locationSource(),
                self.showOnMap(), self.profileCompleted(),
                null, null, null, null, null);
    }

    public ProfileResponse view(UUID selfId, UUID userId) {
        if (selfId.equals(userId)) {
            return mine(selfId);
        }
        User self = get(selfId);
        User other = get(userId);
        viewService.record(self, userId);

        LikeStatus likes = likeService.status(selfId, userId);
        Double meters = userDao.distanceMeters(selfId, userId).orElse(null);
        return new ProfileResponse(other.id(), other.username(), other.firstName(), other.lastName(),
                other.avatarUrl(), other.gender(), other.sexualPreference(), other.biography(),
                other.age(), other.city(), other.fameRating(), presence.isOnline(userId), other.lastSeenAt(),
                interestNames(userId), pictures(userId),
                null, null, null, null, null, null, null,
                ApproximateDistance.km(meters), ApproximateDistance.label(meters), likes.likedByMe(), likes.likesMe(), likes.connected());
    }

    public ProfileResponse update(UUID selfId, ProfileRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        if (request.gender() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pick a gender.", Map.of("gender", "Required."));
        }
        validateBirthDate(request.birthDate());
        SexualPreference preference = request.sexualPreference() == null
                ? SexualPreference.DEFAULT
                : request.sexualPreference();

        String biography = request.biography() == null ? null : request.biography().strip();
        if (biography != null && biography.isEmpty()) {
            biography = null;
        }
        if (biography != null && biography.length() > MAX_BIOGRAPHY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Your biography can be at most " + MAX_BIOGRAPHY_LENGTH + " characters.",
                    Map.of("biography", "Too long."));
        }

        userDao.updateProfile(selfId, request.gender(), preference, biography, request.birthDate());
        userDao.refreshProfileCompleted(selfId);
        return mine(selfId);
    }

    public ProfileResponse updateLocation(UUID selfId, LocationRequest request) {
        if (request == null || request.source() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Say whether the location comes from GPS or MANUAL.",
                    Map.of("source", "Required."));
        }
        Double latitude = request.latitude();
        Double longitude = request.longitude();
        String city = request.city() == null || request.city().isBlank() ? null : request.city().strip();

        if ((latitude == null) != (longitude == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Send both latitude and longitude, or neither.");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Those coordinates are not on Earth.");
        }
        if (request.source() == LocationSource.GPS && latitude == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GPS location needs coordinates.",
                    Map.of("latitude", "Required."));
        }
        if (request.source() == LocationSource.MANUAL && city == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter your city or neighbourhood.",
                    Map.of("city", "Required."));
        }
        Double accuracy = request.source() == LocationSource.GPS ? request.accuracy() : null;
        if (accuracy != null && !(accuracy >= 0 && accuracy <= MAX_GPS_ACCURACY_METERS)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That location accuracy is not believable.",
                    Map.of("accuracy", "Invalid."));
        }
        if (city != null && city.length() > MAX_CITY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The city can be at most " + MAX_CITY_LENGTH + " characters.", Map.of("city", "Too long."));
        }

        // Stored exactly: distances and the map need it. Others only ever see it blurred.
        userDao.updateLocation(selfId, latitude, longitude, accuracy, city, request.source());
        userDao.refreshProfileCompleted(selfId);
        return mine(selfId);
    }

    public ProfileResponse updateMapVisibility(UUID selfId, MapVisibilityRequest request) {
        if (request == null || request.showOnMap() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Say whether to show you on the map.",
                    Map.of("showOnMap", "Required."));
        }
        userDao.updateShowOnMap(selfId, request.showOnMap());
        return mine(selfId);
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter your date of birth.",
                    Map.of("birthDate", "Required."));
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (birthDate.isAfter(today.minusYears(MIN_AGE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You must be at least " + MIN_AGE + ".",
                    Map.of("birthDate", "Too young."));
        }
        if (birthDate.isBefore(today.minusYears(MAX_AGE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That date of birth is not believable.",
                    Map.of("birthDate", "Invalid."));
        }
    }

    private List<String> interestNames(UUID userId) {
        return interestDao.forUser(userId).stream().map(Interest::name).toList();
    }

    private List<PictureResponse> pictures(UUID userId) {
        return pictureDao.forUser(userId).stream().map(PictureResponse::of).toList();
    }

}
