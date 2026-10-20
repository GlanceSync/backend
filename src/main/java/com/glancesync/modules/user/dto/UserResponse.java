package com.glancesync.modules.user.dto;

import java.util.UUID;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.enums.UserRole;

public record UserResponse(UUID id, String username, String avatarUrl, boolean profileCompleted, UserRole role) {

    public static UserResponse of(User user) {
        return new UserResponse(user.id(), user.username(), user.avatarUrl(), user.profileCompleted(), user.role());
    }
}
