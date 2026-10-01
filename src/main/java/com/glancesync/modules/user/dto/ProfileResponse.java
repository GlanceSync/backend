package com.glancesync.modules.user.dto;

import java.util.UUID;
import com.glancesync.modules.user.enums.Gender;
import com.glancesync.modules.user.enums.LocationSource;
import com.glancesync.modules.user.enums.SexualPreference;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Serdeable
public record ProfileResponse(
        UUID id, String username, String firstName, String lastName, String avatarUrl,
        Gender gender, SexualPreference sexualPreference, String biography,
        Integer age, String city, int fameRating, boolean online, Instant lastSeenAt,
        List<String> interests, List<PictureResponse> pictures,
        // Own profile only.
        LocalDate birthDate, Double latitude, Double longitude, Double locationAccuracy,
        LocationSource locationSource, Boolean showOnMap, Boolean completed,
        // Someone else's profile only.
        Integer distanceKm, String distance, Boolean likedByMe, Boolean likesMe, Boolean connected) {
}
