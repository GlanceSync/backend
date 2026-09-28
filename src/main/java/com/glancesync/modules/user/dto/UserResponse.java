package com.glancesync.modules.user.dto;

import java.util.UUID;
import com.glancesync.modules.user.entity.User;
import io.micronaut.serde.annotation.Serdeable;

/**
 * What the client is allowed to know about a user. Never includes the password hash.
 * {@code avatarUrl} is null until they upload a photo. {@code profileCompleted} stays false
 * until every required profile field is filled in; the client keeps the user on the
 * profile form until then.
 */
@Serdeable
public record UserResponse(UUID id, String username, String avatarUrl, boolean profileCompleted) {

    public static UserResponse of(User user) {
        return new UserResponse(user.id(), user.username(), user.avatarUrl(), user.profileCompleted());
    }
}
