package com.glancesync.modules.auth.dto;

import com.glancesync.modules.user.dto.UserResponse;
import io.micronaut.serde.annotation.Serdeable;

/** Answer to "who am I". A null user simply means nobody is signed in. */
@Serdeable
public record SessionResponse(UserResponse user) {
}
