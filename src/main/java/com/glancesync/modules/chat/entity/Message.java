package com.glancesync.modules.chat.entity;

import java.util.UUID;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** One chat message. Sent to clients as-is, so it carries no server-only fields. */
@Serdeable
public record Message(UUID id, UUID senderId, UUID recipientId, String body,
                      Instant createdAt, Instant readAt) {
}
