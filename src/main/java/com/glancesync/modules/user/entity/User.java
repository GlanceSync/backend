package com.glancesync.modules.user.entity;

import java.util.UUID;
import java.time.Instant;

public record User(
    UUID id,
    String username,
    String passwordHash,
    Instant createdAt,
    Instant lastSeenAt,
    Instant avatarUpdatedAt) {

    /**
     * Where the profile photo is served, or null when there is none. The version in the
     * query string changes with every upload, so the image can be cached indefinitely.
     */
    public String avatarUrl() {
        return avatarUrl(id, avatarUpdatedAt);
    }

    public static String avatarUrl(UUID userId, Instant avatarUpdatedAt) {
        return avatarUpdatedAt == null
                ? null
                : "/api/users/" + userId + "/avatar?v=" + avatarUpdatedAt.toEpochMilli();
    }
}
