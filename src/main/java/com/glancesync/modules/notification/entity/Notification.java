package com.glancesync.modules.notification.entity;

import java.util.UUID;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * One notification. {@code actorUsername} is joined in so the client can render the
 * line without a second request.
 */
@Serdeable
public record Notification(UUID id, String type, UUID actorId, String actorUsername,
                           String body, Instant createdAt, Instant readAt) {
}
