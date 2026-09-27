package com.glancesync.modules.user.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * A person in the conversation list: who they are, whether they are online, and a
 * preview of the last thing said, so the list reads like a messenger.
 */
@Serdeable
public record PersonResponse(long id, String username, String avatarUrl, boolean online, Instant lastSeenAt,
                             String lastMessage, Instant lastMessageAt, Boolean lastMessageMine,
                             boolean blocked, boolean blockedByThem) {
}
