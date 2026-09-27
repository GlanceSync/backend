package com.glancesync.modules.user.entity;

import java.util.UUID;
import java.time.Instant;

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
    Instant avatarUpdatedAt) {

    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }

    public String avatarUrl() {
        return avatarUrl(id, avatarUpdatedAt);
    }

    public static String avatarUrl(UUID userId, Instant avatarUpdatedAt) {
        return avatarUpdatedAt == null
                ? null
                : "/api/users/" + userId + "/avatar?v=" + avatarUpdatedAt.toEpochMilli();
    }
}
