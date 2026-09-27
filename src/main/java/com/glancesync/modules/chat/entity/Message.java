package com.glancesync.modules.chat.entity;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** One chat message. Sent to clients as-is, so it carries no server-only fields. */
@Serdeable
public record Message(long id, long senderId, long recipientId, String body,
                      Instant createdAt, Instant readAt) {
}
