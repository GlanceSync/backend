package com.glancesync.api;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Map;

public final class Dtos {

    private Dtos() {
    }

    @Serdeable
    public record RegisterRequest(String username, String password, String confirmPassword) {
    }

    @Serdeable
    public record LoginRequest(String username, String password) {
    }

    /**
     * What the client is allowed to know about a user. Never includes the password hash.
     * {@code avatarUrl} is null until they upload a photo.
     */
    @Serdeable
    public record UserResponse(long id, String username, String avatarUrl) {
    }

    /**
     * A person in the conversation list: who they are, whether they are online, and a
     * preview of the last thing said, so the list reads like a messenger.
     */
    @Serdeable
    public record PersonResponse(long id, String username, String avatarUrl, boolean online, Instant lastSeenAt,
                                 String lastMessage, Instant lastMessageAt, Boolean lastMessageMine,
                                 boolean blocked, boolean blockedByThem) {
    }

    /** Answer to "who am I". A null user simply means nobody is signed in. */
    @Serdeable
    public record SessionResponse(UserResponse user) {
    }

    @Serdeable
    public record ErrorResponse(String message, Map<String, String> fields) {
        public static ErrorResponse of(String message) {
            return new ErrorResponse(message, Map.of());
        }
    }
}
