package com.glancesync.modules.user.entity;

import java.util.UUID;

import com.glancesync.modules.user.enums.Gender;
import com.glancesync.modules.user.enums.LocationSource;
import com.glancesync.modules.user.enums.SexualPreference;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

public record User(
    UUID id,
    String username,
    String email,
    String firstName,
    String lastName,
    String passwordHash,
    Instant emailVerifiedAt,
    Instant createdAt,
    Instant lastSeenAt,
    UUID profilePictureId,
    Gender gender,
    SexualPreference sexualPreference,
    String biography,
    LocalDate birthDate,
    Double latitude,
    Double longitude,
    String city,
    LocationSource locationSource,
    Instant locationUpdatedAt,
    int fameRating,
    boolean profileCompleted) {

    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }

    public Integer age() {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now(ZoneOffset.UTC)).getYears();
    }

    public boolean hasLocation() {
        return locationSource != null;
    }

    public String avatarUrl() {
        return profilePictureId == null ? null : pictureUrl(id, profilePictureId);
    }

    public static String pictureUrl(UUID userId, UUID pictureId) {
        return "/api/users/" + userId + "/pictures/" + pictureId;
    }
}
