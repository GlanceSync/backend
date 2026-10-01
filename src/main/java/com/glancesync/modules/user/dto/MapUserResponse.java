package com.glancesync.modules.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A marker on the map. The position is blurred to a few hundred metres and the distance
 * rounded to the kilometre, so neither gives away where the person actually is.
 */
@Serdeable
public record MapUserResponse(UUID id, String username, String firstName, String avatarUrl, Integer age,
                              boolean online, Instant lastSeenAt, double latitude, double longitude,
                              Double distanceKm, List<String> commonInterests) {
}
