package com.glancesync.modules.auth.dto;

import com.glancesync.modules.user.dto.UserResponse;

public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
}
