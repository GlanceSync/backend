package com.glancesync.modules.interest.entity;

import java.util.UUID;

import java.time.Instant;

public record Interest(UUID id, String name, UUID createdBy, Instant createdAt) {
}
