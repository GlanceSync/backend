package com.glancesync.modules.user.dto;

import java.util.UUID;
import com.glancesync.modules.user.entity.User;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UserResponse(UUID id, String username, String avatarUrl, boolean profileCompleted) {

    public static UserResponse of(User user) {
        return new UserResponse(user.id(), user.username(), user.avatarUrl(), user.profileCompleted());
    }
}
