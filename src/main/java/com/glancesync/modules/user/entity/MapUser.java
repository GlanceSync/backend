package com.glancesync.modules.user.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Someone as the map shows them: a blurred position, the exact distance and shared interests. */
public record MapUser(
    UUID id,
    String username,
    String firstName,
    UUID profilePictureId,
    LocalDate birthDate,
    Instant lastSeenAt,
    double latitude,
    double longitude,
    Double distanceMeters,
    List<String> commonInterests) {

    public Integer age() {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now(ZoneOffset.UTC)).getYears();
    }

    public String avatarUrl() {
        return profilePictureId == null ? null : User.pictureUrl(id, profilePictureId);
    }
}
