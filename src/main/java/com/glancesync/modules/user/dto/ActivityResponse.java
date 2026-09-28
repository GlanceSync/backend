package com.glancesync.modules.user.dto;

import java.util.UUID;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record ActivityResponse(UUID id, String username, String avatarUrl, Instant at, boolean connected) {
}
