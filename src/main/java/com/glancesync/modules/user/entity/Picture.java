package com.glancesync.modules.user.entity;

import java.util.UUID;
import java.time.Instant;

public record Picture(UUID id, UUID userId, boolean profile, Instant createdAt) {

    public String url() {
        return User.pictureUrl(userId, id);
    }
}
