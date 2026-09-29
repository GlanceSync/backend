package com.glancesync.modules.auth.dto;

import com.glancesync.modules.user.dto.UserResponse;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
}
