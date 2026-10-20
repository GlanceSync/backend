package com.glancesync.modules.auth.dto;

import com.glancesync.modules.user.dto.UserResponse;

/** Answer to "who am I". A null user simply means nobody is signed in. */
public record SessionResponse(UserResponse user) {
}
