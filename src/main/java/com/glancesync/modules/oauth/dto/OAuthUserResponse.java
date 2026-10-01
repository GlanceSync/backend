package com.glancesync.modules.oauth.dto;

import io.micronaut.core.annotation.Nullable;

public record OAuthUserResponse(String providerId, @Nullable String username, @Nullable String email,
                                boolean emailVerified, @Nullable String firstName, @Nullable String lastName,
                                @Nullable String imageUrl) {
}
