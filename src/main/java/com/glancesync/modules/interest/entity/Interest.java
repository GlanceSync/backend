package com.glancesync.modules.interest.entity;

import java.util.UUID;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record Interest(UUID id, String name, UUID createdBy, Instant createdAt) {
}
