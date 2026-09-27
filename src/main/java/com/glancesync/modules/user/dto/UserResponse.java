package com.glancesync.modules.user.dto;

import io.micronaut.serde.annotation.Serdeable;

/**
 * What the client is allowed to know about a user. Never includes the password hash.
 * {@code avatarUrl} is null until they upload a photo.
 */
@Serdeable
public record UserResponse(long id, String username, String avatarUrl) {
}
