package com.glancesync.modules.user.dto;

import java.util.UUID;

import java.time.Instant;

public record ActivityResponse(UUID id, String username, String avatarUrl, Instant at, boolean connected) {
}
