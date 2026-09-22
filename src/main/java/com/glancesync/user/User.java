package com.glancesync.user;

import java.time.Instant;

public record User(long id, String username, String passwordHash, Instant createdAt, Instant lastSeenAt) {
}
